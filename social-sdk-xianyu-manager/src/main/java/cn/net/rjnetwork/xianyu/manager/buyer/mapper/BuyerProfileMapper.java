package cn.net.rjnetwork.xianyu.manager.buyer.mapper;

import cn.net.rjnetwork.xianyu.manager.buyer.model.BuyerProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BuyerProfileMapper extends BaseMapper<BuyerProfile> {
    @Select("SELECT * FROM buyer_profile WHERE buyer_id = #{buyerId} AND deleted = 0 LIMIT 1")
    BuyerProfile selectByBuyerId(@Param("buyerId") String buyerId);
}
