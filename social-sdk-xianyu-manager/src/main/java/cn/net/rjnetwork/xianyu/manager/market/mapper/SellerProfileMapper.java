package cn.net.rjnetwork.xianyu.manager.market.mapper;

import cn.net.rjnetwork.xianyu.manager.market.model.SellerProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SellerProfileMapper extends BaseMapper<SellerProfile> {
    @Select("SELECT * FROM seller_profile WHERE user_id = #{userId} AND deleted = 0 LIMIT 1")
    SellerProfile selectByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM seller_profile WHERE nickname LIKE '%' || #{kw} || '%' AND deleted = 0 LIMIT 20")
    java.util.List<SellerProfile> search(@Param("kw") String kw);
}
