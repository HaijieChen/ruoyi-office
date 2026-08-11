package cn.iocoder.yudao.module.infra.api.file;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 本地 Bean 实现，不做 RestController，防止 /rpc-api 匿名暴露原始内容。
 * <p>
 * 仅在 yudao-server 单体中与 HRM 同进程可用。
 */
@Service
public class FileAccessApiImpl implements FileAccessApi {

    @Resource
    private FileService fileService;
    @Resource
    private FileMapper fileMapper;

    @Override
    public FileRespDTO createFile(byte[] content, String name, String directory, String type) {
        // 允许私有目录：仅本本地 Bean 入口（HRM claim）
        FileDO file = fileService.createFileReturnAllowPrivate(content, name, directory, type);
        return BeanUtils.toBean(file, FileRespDTO.class);
    }

    @Override
    public FileRespDTO getFile(Long id) {
        if (id == null) {
            return null;
        }
        FileDO file = fileMapper.selectById(id);
        return file == null ? null : BeanUtils.toBean(file, FileRespDTO.class);
    }

    @Override
    public FileRespDTO getUniqueFileByPath(String path) {
        if (StrUtil.isBlank(path)) {
            return null;
        }
        List<FileDO> list = fileMapper.selectList(FileDO::getPath, path);
        if (list == null || list.size() != 1) {
            return null; // 0 或多条 → 歧义，不猜测
        }
        return BeanUtils.toBean(list.get(0), FileRespDTO.class);
    }

    @Override
    public FileRespDTO getUniqueFileByUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        List<FileDO> list = fileMapper.selectList(FileDO::getUrl, url);
        if (list == null || list.size() != 1) {
            return null;
        }
        return BeanUtils.toBean(list.get(0), FileRespDTO.class);
    }

    @Override
    public byte[] getFileContent(Long id) {
        FileDO file = fileMapper.selectById(id);
        if (file == null || file.getConfigId() == null || StrUtil.isBlank(file.getPath())) {
            return null;
        }
        // 禁止无 configId 的裸 path 读取；私有 path 走 AllowPrivate
        try {
            return fileService.getFileContentAllowPrivate(file.getConfigId(), file.getPath());
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + id, e);
        }
    }

}
