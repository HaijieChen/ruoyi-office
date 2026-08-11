package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;

/**
 * 文件访问 API（Spring 本地 Bean，非 HTTP/RPC 暴露）。
 * <p>
 * 敏感业务请通过此接口读取内容，避免走 permitAll 的 /rpc-api 或公开下载路由。
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
     * 按 path 查找（历史附件回填/兼容读）
     */
    FileRespDTO getFileByPath(String path);

    /**
     * 按 url 查找
     */
    FileRespDTO getFileByUrl(String url);

    /**
     * 按编号读取内容
     */
    byte[] getFileContent(Long id);

    /**
     * 按 configId+path 读取内容（兼容无 file 行时的本地存储）
     */
    byte[] getFileContent(Long configId, String path);

}
