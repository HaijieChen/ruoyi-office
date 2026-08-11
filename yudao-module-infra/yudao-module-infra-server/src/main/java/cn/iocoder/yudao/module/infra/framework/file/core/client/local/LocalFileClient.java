package cn.iocoder.yudao.module.infra.framework.file.core.client.local;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.iocoder.yudao.module.infra.framework.file.core.client.AbstractFileClient;

import java.io.File;
import java.io.IOException;

/**
 * 本地文件客户端
 *
 * @author 宇擎源码
 */
public class LocalFileClient extends AbstractFileClient<LocalFileClientConfig> {

    public LocalFileClient(Long id, LocalFileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
    }

    @Override
    public String upload(byte[] content, String path, String type) {
        // 执行写入
        String filePath = getFilePath(path);
        FileUtil.writeBytes(content, filePath);
        // 拼接返回路径
        return super.formatFileUrl(config.getDomain(), path);
    }

    @Override
    public void delete(String path) {
        String filePath = getFilePath(path);
        FileUtil.del(filePath);
    }

    @Override
    public byte[] getContent(String path) {
        String filePath = getFilePath(path);
        try {
            return FileUtil.readBytes(filePath);
        } catch (IORuntimeException ex) {
            if (ex.getMessage().startsWith("File not exist:")) {
                return null;
            }
            throw ex;
        }
    }

    /**
     * 规范化并校验 path 必须落在 basePath 根目录内，防止 ../ 路径穿越。
     */
    String getFilePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("文件 path 不能为空");
        }
        try {
            File base = new File(config.getBasePath()).getCanonicalFile();
            File target = new File(base, path).getCanonicalFile();
            String basePath = base.getPath();
            String targetPath = target.getPath();
            if (!targetPath.equals(basePath) && !targetPath.startsWith(basePath + File.separator)) {
                throw new IllegalArgumentException("非法文件路径（越出存储根目录）: " + path);
            }
            return targetPath;
        } catch (IOException ex) {
            throw new IllegalArgumentException("非法文件路径: " + path, ex);
        }
    }

}
