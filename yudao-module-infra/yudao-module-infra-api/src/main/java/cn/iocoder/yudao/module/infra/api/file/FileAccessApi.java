package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;

/**
 * 文件访问 API（Spring 本地 Bean，非 HTTP/RPC 暴露）。
 * <p>
 * 敏感业务请通过此接口读取内容，避免走 permitAll 的 /rpc-api 或公开下载路由。
 * <p>
 * <b>部署约束（EXP-75）：</b>仅支持 {@code yudao-server} 单体（infra-server 与 hrm-server 同进程）。
 * 独立启动 HrmServerApplication 无法注入本 Bean，不在支持范围。
 */
public interface FileAccessApi {

    /**
     * 上传并返回完整元数据（含权威 id）
     */
    FileRespDTO createFile(byte[] content, String name, String directory, String type);

    /**
     * 按编号获取元数据
     */
    FileRespDTO getFile(Long id);

    /**
     * 按 path 唯一查找；0 或 &gt;1 条均返回 null（歧义不取第一条）
     */
    FileRespDTO getUniqueFileByPath(String path);

    /**
     * 按 url 唯一查找；0 或 &gt;1 条均返回 null
     */
    FileRespDTO getUniqueFileByUrl(String url);

    /**
     * 按编号读取内容（configId+path 来自权威 file 行）
     */
    byte[] getFileContent(Long id);

}
