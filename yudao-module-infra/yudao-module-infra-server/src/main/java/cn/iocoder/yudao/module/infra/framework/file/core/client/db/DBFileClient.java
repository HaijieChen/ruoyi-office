package cn.iocoder.yudao.module.infra.framework.file.core.client.db;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileContentDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileContentMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.AbstractFileClient;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONTENT_TOO_LARGE;

/**
 * 基于 DB 存储的文件客户端的配置类
 *
 * @author 宇擎源码
 */
public class DBFileClient extends AbstractFileClient<DBFileClientConfig> {

    private FileContentMapper fileContentMapper;

    public DBFileClient(Long id, DBFileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
        fileContentMapper = SpringUtil.getBean(FileContentMapper.class);
    }

    @Override
    public String upload(byte[] content, String path, String type) {
        FileContentDO contentDO = new FileContentDO().setConfigId(getId())
                .setPath(path).setContent(content);
        try {
            fileContentMapper.insert(contentDO);
        } catch (DataIntegrityViolationException ex) {
            // Data truncation: Data too long for column 'content'（mediumblob）→ 稳定业务 4xx，避免 500
            throw exception(FILE_CONTENT_TOO_LARGE);
        } catch (RuntimeException ex) {
            if (isDataTruncation(ex)) {
                throw exception(FILE_CONTENT_TOO_LARGE);
            }
            throw ex;
        }
        // 拼接返回路径
        return super.formatFileUrl(config.getDomain(), path);
    }

    /** 供单测 / 全局处理器复用：识别 MySQL 截断类错误 */
    public static boolean isDataTruncation(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("data truncation")
                        || m.contains("data too long")
                        || m.contains("max_allowed_packet")
                        || m.contains("packet for query is too large")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    @Override
    public void delete(String path) {
        fileContentMapper.deleteByConfigIdAndPath(getId(), path);
    }

    @Override
    public byte[] getContent(String path) {
        List<FileContentDO> list = fileContentMapper.selectListByConfigIdAndPath(getId(), path);
        if (CollUtil.isEmpty(list)) {
            return null;
        }
        // 排序后，拿 id 最大的，即最后上传的
        list.sort(Comparator.comparing(FileContentDO::getId));
        return CollUtil.getLast(list).getContent();
    }

}
