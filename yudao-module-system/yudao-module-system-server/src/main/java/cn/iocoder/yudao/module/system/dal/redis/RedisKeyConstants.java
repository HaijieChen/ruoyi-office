package cn.iocoder.yudao.module.system.dal.redis;

import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;

/**
 * System Redis Key 枚举类
 *
 * @author 宇擎源码
 */
public interface RedisKeyConstants {

    /**
     * 指定部门的所有子部门编号数组的缓存（<b>遗留命名空间</b>）。
     * <p>
     * KEY 格式：dept_children_ids:{id}<br>
     * VALUE：历史版本为 {@code Set&lt;Long&gt;}（{@code @Cacheable} 时代）。
     * <p>
     * EXP-86 起<strong>新代码不再读写本命名空间中的 stamped 类型</strong>，仅在组织写成功后
     * 顺带 clear，帮助滚动升级中仍跑旧包的实例失效陈旧 Set。
     * 新值写入 {@link #DEPT_CHILDREN_ID_LIST_V2}（C1：避免旧实例反序列化新类失败）。
     */
    String DEPT_CHILDREN_ID_LIST = "dept_children_ids";

    /**
     * 组织子树缓存 V2（EXP-86+ 代际 cache-aside）。
     * <p>
     * KEY 格式：dept_children_ids_v2:{id}<br>
     * VALUE：{@code DeptChildrenCacheInvalidator.GenerationStampedSet}<br>
     * 与 {@link #DEPT_CHILDREN_ID_LIST} <strong>命名空间隔离</strong>：滚动部署时旧实例只读
     * 旧名，永不反序列化本空间中的新类型；回滚后新实例停止写本空间，旧实例继续只读旧名。
     */
    String DEPT_CHILDREN_ID_LIST_V2 = "dept_children_ids_v2";

    /**
     * 角色的缓存
     * <p>
     * KEY 格式：role:{id}
     * VALUE 数据类型：String 角色信息
     */
    String ROLE = "role";

    /**
     * 用户拥有的角色编号的缓存
     * <p>
     * KEY 格式：user_role_ids:{userId}
     * VALUE 数据类型：String 角色编号集合
     */
    String USER_ROLE_ID_LIST = "user_role_ids";

    /**
     * 拥有指定菜单的角色编号的缓存
     * <p>
     * KEY 格式：user_role_ids:{menuId}
     * VALUE 数据类型：String 角色编号集合
     */
    String MENU_ROLE_ID_LIST = "menu_role_ids";

    /**
     * 拥有权限对应的菜单编号数组的缓存
     * <p>
     * KEY 格式：permission_menu_ids:{permission}
     * VALUE 数据类型：String 菜单编号数组
     */
    String PERMISSION_MENU_ID_LIST = "permission_menu_ids";

    /**
     * OAuth2 客户端的缓存
     * <p>
     * KEY 格式：oauth_client:{id}
     * VALUE 数据类型：String 客户端信息
     */
    String OAUTH_CLIENT = "oauth_client";

    /**
     * 访问令牌的缓存
     * <p>
     * KEY 格式：oauth2_access_token:{token}
     * VALUE 数据类型：String 访问令牌信息 {@link OAuth2AccessTokenDO}
     * <p>
     * 由于动态过期时间，使用 RedisTemplate 操作
     */
    String OAUTH2_ACCESS_TOKEN = "oauth2_access_token:%s";

    /**
     * 站内信模版的缓存
     * <p>
     * KEY 格式：notify_template:{code}
     * VALUE 数据格式：String 模版信息
     */
    String NOTIFY_TEMPLATE = "notify_template";

    /**
     * 邮件账号的缓存
     * <p>
     * KEY 格式：mail_account:{id}
     * VALUE 数据格式：String 账号信息
     */
    String MAIL_ACCOUNT = "mail_account";

    /**
     * 邮件模版的缓存
     * <p>
     * KEY 格式：mail_template:{code}
     * VALUE 数据格式：String 模版信息
     */
    String MAIL_TEMPLATE = "mail_template";

    /**
     * 短信模版的缓存
     * <p>
     * KEY 格式：sms_template:{id}
     * VALUE 数据格式：String 模版信息
     */
    String SMS_TEMPLATE = "sms_template";

    /**
     * 小程序订阅模版的缓存
     *
     * KEY 格式：wxa_subscribe_template:{userType}
     * VALUE 数据格式 String, 模版信息
     */
    String WXA_SUBSCRIBE_TEMPLATE = "wxa_subscribe_template";

}
