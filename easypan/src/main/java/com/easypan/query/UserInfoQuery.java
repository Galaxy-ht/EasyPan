package com.easypan.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
* 用户CRUD查询
*
* @author Tao MrHaoTao@gmail.com
* @since 1.0.0 2023-08-16
*/
@Data
@EqualsAndHashCode(callSuper = false)
public class UserInfoQuery extends Query {

    private String nickNameFuzzy;

    private Integer status;
}