package cn.net.rjnetwork.xianyu.manager.market.mapper;

import cn.net.rjnetwork.xianyu.manager.market.model.MarketKeyword;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MarketKeywordMapper extends BaseMapper<MarketKeyword> {

    /** 查询所有未删除的追踪关键词 */
    @Select("SELECT * FROM market_keyword WHERE deleted = 0 ORDER BY created_at DESC")
    List<MarketKeyword> selectAllActive();

    /** 按关键词精确查找（含 deleted 判断） */
    @Select("SELECT * FROM market_keyword WHERE keyword = #{keyword} AND deleted = 0 LIMIT 1")
    MarketKeyword selectByKeyword(@Param("keyword") String keyword);
}
