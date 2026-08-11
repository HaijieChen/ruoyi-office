package cn.iocoder.yudao.module.infra.api.file;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.dto.FileRespDTO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.service.file.FileConfigService;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * 本地 Bean 实现，不做 RestController，防止 /rpc-api 匿名暴露原始内容。
 */
@Service
public class FileAccessApiImpl implements FileAccessApi {

    @Resource
    private FileService fileService;
    @Resource
    private FileMapper fileMapper;
    @Resource
    private FileConfigService fileConfigService;

    @Override
    public FileRespDTO createFile(byte[] content, String name, String directory, String type) {
        FileDO file = fileService.createFileReturn(content, name, directory, type);
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
    public FileRespDTO getFileByPath(String path) {
        if (StrUtil.isBlank(path)) {
            return null;
        }
        FileDO file = fileMapper.selectOne(FileDO::getPath, path);
        return file == null ? null : BeanUtils.toBean(file, FileRespDTO.class);
    }

    @Override
    public FileRespDTO getFileByUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        FileDO file = fileMapper.selectOne(FileDO::getUrl, url);
        return file == null ? null : BeanUtils.toBean(file, FileRespDTO.class);
    }

    @Override
    public byte[] getFileContent(Long id) {
        FileDO file = fileMapper.selectById(id);
        if (file == null) {
            return null;
        }
        try {
            return fileService.getFileContent(file.getConfigId(), file.getPath());
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + id, e);
        }
    }

    @Override
    public byte[] getFileContent(Long configId, String path) {
        try {
            if (configId != null) {
                return fileService.getFileContent(configId, path);
            }
            // 历史兼容：无 configId 时走 master 客户端
            FileClient client = fileConfigService.getMasterFileClient();
            Assert.notNull(client, "客户端(master) 不能为空");
            return client.getContent(path);
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + path, e);
        }
    }

}
