^package cn.net.rjnetwork.xianyu.manager.wallet.mapper;

import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWalletTransaction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WalletTransactionMapper extends BaseMapper<XianyuWalletTransaction> {
}
