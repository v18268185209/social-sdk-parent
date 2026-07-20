package cn.net.rjnetwork.xianyu.captcha.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 物理轨迹引擎 - 生成人类化滑块轨迹
 * <p>核心算法优于 xianyu-auto-bot：
 * <ul>
 *   <li>基于 Fitts 定律动态步数</li>
 *   <li>Perlin 噪声生成连续 Y 轴抖动（低频手臂移动 + 高频手指颤抖）</li>
 *   <li>三次贝塞尔曲线控制 X 轴轨迹</li>
 *   <li>超调 (overshoot) + 回退 (retreat) 两段式主滑动</li>
 *   <li>自适应参数学习（成功后持久化）</li>
 *   <li>每次重试自动加大扰动幅度</li>
 * </ul>
 * </p>
 */
public class SliderTrajectoryEngine {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 轨迹点: [dx, dy, dt] 相对X位移, 相对Y位移, 时间间隔(秒) */
    public static class TrajectoryPoint {
        public final double dx;
        public final double dy;
        public final double dt;

        public TrajectoryPoint(double dx, double dy, double dt) {
            this.dx = dx;
            this.dy = dy;
            this.dt = dt;
        }

        public double[] toArray() {
            return new double[]{dx, dy, dt};
        }
    }

    /** 轨迹生成参数（可序列化用于学习） */
    public static class TrajectoryParams {
        // Fitts 定律步数
        public int steps = 25;           // 动态计算后设置
        // 超调比例
        public double overshootRatio = 1.05;  // 1.03~1.15
        // 回退距离比例
        public double retreatRatio = 0.08;    // 0.05~0.10
        // 基础延迟
        public double baseDelay = 0.008;      // 0.004~0.015
        // 加速曲线指数
        public double accelerationCurve = 1.8; // 1.5~2.2
        // Y 轴抖动最大值
        public double yJitterMax = 2.0;       // 1.0~3.0
        // Perlin 噪声种子
        public double ySeed1 = -1;            // -1 表示随机
        public double ySeed2 = -1;
        public double delaySeed = -1;
        // 贝塞尔控制点随机因子
        public double bezierP1 = -1;          // -1 表示随机
        public double bezierP2 = -1;
        // 延迟波动因子
        public double delayVariationMin = 0.85;
        public double delayVariationMax = 1.15;
        // 随机暂停概率
        public double hesitationProb = 0.06;  // 0.04~0.10
        // 抖动频率因子
        public double yFreq1 = 3.0;           // 2.0~4.0
        public double yFreq2 = 8.0;           // 6.0~10.0

        public static TrajectoryParams random(TrajectoryParams base, int attempt) {
            TrajectoryParams p = new TrajectoryParams();

            // 根据尝试次数加大扰动
            double jitterMultiplier = 1.0 + (attempt - 1) * 0.3;

            TrajectoryParams source = base != null ? base : defaultParams();
            p.steps = source.steps + (RANDOM.nextInt(5) - 2);
            p.steps = Math.max(18, Math.min(45, p.steps));

            p.overshootRatio = clamp(source.overshootRatio + (RANDOM.nextDouble() - 0.5) * 0.05 * jitterMultiplier, 1.0, 1.25);
            p.retreatRatio = clamp(source.retreatRatio + (RANDOM.nextDouble() - 0.5) * 0.02, 0.03, 0.12);
            p.baseDelay = clamp(source.baseDelay + (RANDOM.nextDouble() - 0.5) * 0.003 * jitterMultiplier, 0.003, 0.02);
            p.accelerationCurve = clamp(source.accelerationCurve + (RANDOM.nextDouble() - 0.5) * 0.3, 1.3, 2.5);
            p.yJitterMax = clamp(source.yJitterMax + (RANDOM.nextDouble() - 0.5) * 0.8 * jitterMultiplier, 0.5, 4.0);

            p.ySeed1 = RANDOM.nextDouble() * 1000;
            p.ySeed2 = RANDOM.nextDouble() * 1000;
            p.delaySeed = RANDOM.nextDouble() * 1000;
            p.bezierP1 = 0.2 + RANDOM.nextDouble() * 0.15;
            p.bezierP2 = 0.7 + RANDOM.nextDouble() * 0.15;

            p.delayVariationMin = 0.85 + RANDOM.nextDouble() * 0.1;
            p.delayVariationMax = 1.05 + RANDOM.nextDouble() * 0.1;
            p.hesitationProb = clamp(base.hesitationProb + (RANDOM.nextDouble() - 0.5) * 0.03, 0.02, 0.15);
            p.yFreq1 = 2.0 + RANDOM.nextDouble() * 2.0;
            p.yFreq2 = 6.0 + RANDOM.nextDouble() * 4.0;

            return p;
        }

        public static TrajectoryParams defaultParams() {
            return new TrajectoryParams();
        }
    }

    private SliderTrajectoryEngine() {}

    /**
     * 生成人类化轨迹
     *
     * @param distance 滑块需要移动的像素距离
     * @param params   轨迹参数（null 则用默认随机参数）
     * @return 轨迹点列表
     */
    public static List<TrajectoryPoint> generate(double distance, TrajectoryParams params) {
        if (params == null) {
            params = TrajectoryParams.defaultParams();
        }
        // Fitts 定律动态步数
        double fittsFactor = (Math.log(Math.max(1, distance / 50.0 + 1)) / Math.log(2)) / (Math.log(7) / Math.log(2));
        params.steps = (int) Math.round(params.steps * Math.max(0.7, Math.min(1.3, fittsFactor)));
        params.steps = Math.max(18, Math.min(45, params.steps));

        List<TrajectoryPoint> trajectory = new ArrayList<>();

        double overshootTarget = distance * params.overshootRatio;
        // 回退必须回到目标距离附近释放；不能只回退 overshoot 的一小部分，否则最终释放点仍在轨道外侧。
        double retreatDistance = Math.max(0, overshootTarget - distance);

        // 阶段 1: 主滑动 (75% 步数)
        int mainSteps = (int) (params.steps * 0.75);
        double p0 = 0;
        double p1 = overshootTarget * params.bezierP1;
        double p2 = overshootTarget * params.bezierP2;
        double p3 = overshootTarget;

        double prevX = 0, prevY = 0;
        double totalDt = 0;

        for (int i = 1; i <= mainSteps; i++) {
            double t = (i / (double) mainSteps);
            // ease-out 缓动
            double easedT = 1 - Math.pow(1 - t, params.accelerationCurve);
            // 三次贝塞尔
            double x = cubicBezier(p0, p1, p2, p3, easedT);

            // Y 轴: 低频 Perlin + 高频 Perlin + 微弱随机
            double yLow = perlinOctaves(t * params.yFreq1, params.ySeed1) * params.yJitterMax * 0.65;
            double yHigh = perlin(t * params.yFreq2, params.ySeed2) * params.yJitterMax * 0.35;
            double y = yLow + yHigh + (RANDOM.nextDouble() - 0.5) * 0.4;

            // 延迟: 基础延迟 + sin 速度包络 + Perlin 抖动
            double speedFactor = Math.sin(t * Math.PI);
            if (speedFactor < 0.1) speedFactor = 0.1;
            double delayJitter = 1.0 + perlin(t * 5.0, params.delaySeed) * 0.15;
            double dt = (params.baseDelay / speedFactor) * delayJitter;

            // 随机暂停
            if (0.2 < t && t < 0.8 && RANDOM.nextDouble() < params.hesitationProb) {
                dt += 0.01 + RANDOM.nextDouble() * 0.03;
            }

            // 添加生理性颤抖
            x += (RANDOM.nextDouble() - 0.5) * 0.5;

            trajectory.add(new TrajectoryPoint(x, y, dt));
            totalDt += dt;
            prevX = x;
            prevY = y;
        }

        // 阶段 2: 回退 (25% 步数)
        int retreatSteps = params.steps - mainSteps;
        if (retreatSteps > 0 && retreatDistance > 0) {
            for (int i = 1; i <= retreatSteps; i++) {
                double t = i / (double) retreatSteps;
                double easedT = smoothstep(t);
                double x = overshootTarget - retreatDistance * easedT;
                double y = prevY * (1 - t) + (RANDOM.nextDouble() - 0.5) * params.yJitterMax * 0.3;
                double dt = params.baseDelay * (1.2 + RANDOM.nextDouble() * 0.6);

                trajectory.add(new TrajectoryPoint(x, y, dt));
                totalDt += dt;
            }
        }

        return trajectory;
    }

    /**
     * 为当前尝试生成扰动后的轨迹
     */
    public static List<TrajectoryPoint> generateWithRetry(double distance, int attempt) {
        TrajectoryParams base = TrajectoryParams.defaultParams();
        TrajectoryParams params = TrajectoryParams.random(base, attempt);
        return generate(distance, params);
    }

    // ─── 数学工具 ────────────────────────────────────────

    private static double cubicBezier(double p0, double p1, double p2, double p3, double t) {
        double u = 1 - t;
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // ─── Perlin 噪声 1D ─────────────────────────────────

    /**
     * 一维 Perlin 噪声（基于梯度 hash）
     */
    public static double perlin(double x, double seedOffset) {
        long seed = Double.doubleToRawLongBits(seedOffset);
        long x0 = (long) Math.floor(x);
        long x1 = x0 + 1;
        double sx = x - x0;

        double n0 = grad1D(hash(x0, seed), sx);
        double n1 = grad1D(hash(x1, seed), sx - 1);

        double u = fade(sx);
        return lerp(n0, n1, u);
    }

    /**
     * 八度 Perlin 噪声（多层叠加）
     */
    public static double perlinOctaves(double x, double seedOffset) {
        double sum = 0;
        double amplitude = 1.0;
        double totalAmplitude = 0;
        int octaves = 2;

        for (int i = 0; i < octaves; i++) {
            sum += perlin(x * (1 << i), seedOffset + i * 100) * amplitude;
            totalAmplitude += amplitude;
            amplitude *= 0.5;
        }
        return sum / totalAmplitude;
    }

    private static long hash(long x, long seed) {
        long h = x * 0x45d9f3bL + seed * 0x1b873593L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private static double grad1D(long hash, double x) {
        return ((hash & 1) == 0) ? x : -x;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
}
