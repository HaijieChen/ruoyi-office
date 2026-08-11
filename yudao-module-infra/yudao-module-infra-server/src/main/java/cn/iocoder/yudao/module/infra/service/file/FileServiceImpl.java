package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.FilePrivateDirs;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static cn.hutool.core.date.DatePattern.PURE_DATE_PATTERN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;

/**
 * 文件 Service 实现类
 *
 * @author 宇擎源码
 */
@Service
public class FileServiceImpl implements FileService {

    /**
     * 上传文件的前缀，是否包含日期（yyyyMMdd）
     *
     * 目的：按照日期，进行分目录
     */
    static boolean PATH_PREFIX_DATE_ENABLE = true;
    /**
     * 上传文件的后缀，是否包含时间戳
     *
     * 目的：保证文件的唯一性，避免覆盖
     * 定制：可按需调整成 UUID、或者其他方式
     */
    static boolean PATH_SUFFIX_TIMESTAMP_ENABLE = true;

    @Resource
    private FileConfigService fileConfigService;

    @Resource
    private FileMapper fileMapper;

    @Override
    public PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO) {
        // 通用分页：排除私有目录（service 层隔离，不依赖前端过滤）
        return fileMapper.selectPagePublic(pageReqVO);
    }

    @Override
    @SneakyThrows
    public String createFile(byte[] content, String name, String directory, String type) {
        return createFileReturn(content, name, directory, type).getUrl();
    }

    @Override
    @SneakyThrows
    public FileDO createFileReturn(byte[] content, String name, String directory, String type) {
        return doCreateFileReturn(content, name, directory, type, false);
    }

    @Override
    @SneakyThrows
    public FileDO createFileReturnAllowPrivate(byte[] content, String name, String directory, String type) {
        return doCreateFileReturn(content, name, directory, type, true);
    }

    @SneakyThrows
    private FileDO doCreateFileReturn(byte[] content, String name, String directory, String type,
                                      boolean allowPrivate) {
        // 1.1 处理 type 为空的情况
        if (StrUtil.isEmpty(type)) {
            type = FileTypeUtils.getMineType(content, name);
        }
        // 1.2 处理 name 为空的情况
        if (StrUtil.isEmpty(name)) {
            name = DigestUtil.sha256Hex(content);
        }
        if (StrUtil.isEmpty(FileUtil.extName(name))) {
            // 如果 name 没有后缀 type，则补充后缀
            String extension = FileTypeUtils.getExtension(type);
            if (StrUtil.isNotEmpty(extension)) {
                name = name + extension;
            }
        }

        // 2.1 生成上传的 path，需要保证唯一
        String path = generateUploadPath(name, directory);
        // 2.2 校验最终 path（防 name 注入私有前缀）
        rejectPrivateStoragePath(path, allowPrivate);
        // 2.3 上传到文件存储器
        FileClient client = fileConfigService.getMasterFileClient();
        Assert.notNull(client, "客户端(master) 不能为空");
        String url = client.upload(content, path, type);

        // 3. 保存到数据库
        FileDO file = new FileDO().setConfigId(client.getId())
                .setName(name).setPath(path).setUrl(url)
                .setType(type).setSize((long) content.length);
        fileMapper.insert(file);
        return file;
    }

    @VisibleForTesting
    String generateUploadPath(String name, String directory) {
        // 1. 生成前缀、后缀
        String prefix = null;
        if (PATH_PREFIX_DATE_ENABLE) {
            prefix = LocalDateTimeUtil.format(LocalDateTimeUtil.now(), PURE_DATE_PATTERN);
        }
        String suffix = null;
        if (PATH_SUFFIX_TIMESTAMP_ENABLE) {
            suffix = String.valueOf(System.currentTimeMillis());
        }

        // 2.1 先拼接 suffix 后缀
        if (StrUtil.isNotEmpty(suffix)) {
            String ext = FileUtil.extName(name);
            if (StrUtil.isNotEmpty(ext)) {
                name = FileUtil.mainName(name) + StrUtil.C_UNDERLINE + suffix + StrUtil.DOT + ext;
            } else {
                name = name + StrUtil.C_UNDERLINE + suffix;
            }
        }
        // 2.2 再拼接 prefix 前缀
        if (StrUtil.isNotEmpty(prefix)) {
            name = prefix + StrUtil.SLASH + name;
        }
        // 2.3 最后拼接 directory 目录
        if (StrUtil.isNotEmpty(directory)) {
            name = directory + StrUtil.SLASH + name;
        }
        return name;
    }

    /**
     * 通用入口拒绝最终 path 落入私有命名空间（含 name 注入）。
     */
    @VisibleForTesting
    void rejectPrivateStoragePath(String path, boolean allowPrivate) {
        if (!allowPrivate && FilePrivateDirs.isPrivateDirectory(path)) {
            throw new IllegalArgumentException(
                    "最终 path 含入职资料私有目录，禁止经通用上传/预签名写入: " + path);
        }
    }

    @Override
    @SneakyThrows
    public FilePresignedUrlRespVO presignPutUrl(String name, String directory) {
        // 1. 生成上传的 path，需要保证唯一
        String path = generateUploadPath(name, directory);
        // 校验最终 path（防 name=hrm-onboarding-private/... 无扩展名注入）
        rejectPrivateStoragePath(path, false);

        // 2. 获取文件预签名地址
        FileClient fileClient = fileConfigService.getMasterFileClient();
        String uploadUrl = fileClient.presignPutUrl(path);
        String visitUrl = fileClient.presignGetUrl(path, null);
        return new FilePresignedUrlRespVO().setConfigId(fileClient.getId())
                .setPath(path).setUploadUrl(uploadUrl).setUrl(visitUrl);
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        // 读预签名：拒绝私有 path/url
        rejectPrivateStoragePath(url, false);
        FileClient fileClient = fileConfigService.getMasterFileClient();
        return fileClient.presignGetUrl(url, expirationSeconds);
    }

    @Override
    public Long createFile(FileCreateReqVO createReqVO) {
        rejectPrivateStoragePath(createReqVO.getPath(), false);
        createReqVO.setUrl(HttpUtils.removeUrlQuery(createReqVO.getUrl())); // 目的：移除私有桶情况下，URL 的签名参数
        FileDO file = BeanUtils.toBean(createReqVO, FileDO.class);
        fileMapper.insert(file);
        return file.getId();
    }

    @Override
    public FileDO getFile(Long id) {
        FileDO file = validateFileExists(id);
        // 通用 get：私有文件 / 入职绑定 path 对 infra:file:query 不可见
        rejectPrivateStoragePath(file.getPath(), false);
        rejectReservedAttachmentBoundPath(file.getPath());
        return file;
    }

    @Override
    public void deleteFile(Long id) throws Exception {
        // 校验存在
        FileDO file = validateFileExists(id);
        rejectPrivateStoragePath(file.getPath(), false);
        rejectReservedAttachmentBoundPath(file.getPath());

        // 从文件存储器中删除
        FileClient client = fileConfigService.getFileClient(file.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", file.getConfigId());
        client.delete(file.getPath());

        // 删除记录
        fileMapper.deleteById(id);
    }

    @Override
    @SneakyThrows
    public void deleteFileList(List<Long> ids) {
        // 删除文件（跳过/拒绝私有）
        List<FileDO> files = fileMapper.selectByIds(ids);
        List<Long> publicIds = files.stream()
                .filter(f -> !FilePrivateDirs.isPrivateDirectory(f.getPath()))
                .map(FileDO::getId)
                .collect(Collectors.toList());
        if (publicIds.size() != files.size()) {
            throw new IllegalArgumentException("批量删除包含入职资料私有文件，已拒绝");
        }
        for (FileDO file : files) {
            FileClient client = fileConfigService.getFileClient(file.getConfigId());
            Assert.notNull(client, "客户端({}) 不能为空", file.getPath());
            client.delete(file.getPath());
        }
        fileMapper.deleteByIds(ids);
    }

    private FileDO validateFileExists(Long id) {
        FileDO fileDO = fileMapper.selectById(id);
        if (fileDO == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        return fileDO;
    }

    @Override
    public byte[] getFileContent(Long configId, String path) throws Exception {
        // 匿名/通用下载：拒私有目录 + 拒绑定入职资料/入职单的历史 path
        rejectPrivateStoragePath(path, false);
        rejectReservedAttachmentBoundPath(path);
        FileClient client = fileConfigService.getFileClient(configId);
        Assert.notNull(client, "客户端({}) 不能为空", configId);
        return client.getContent(path);
    }

    /**
     * 历史入职对象即使 path 无私有前缀，也不允许经通用/匿名 File 路由读取。
     */
    private void rejectReservedAttachmentBoundPath(String path) {
        if (StrUtil.isBlank(path)) {
            return;
        }
        try {
            Long cnt = fileMapper.countReservedAttachmentByPath(path);
            if (cnt != null && cnt > 0) {
                throw new IllegalArgumentException("path 绑定入职资料/入职单附件，禁止通用下载: " + path);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ignored) {
            // common_attachment 表在纯 infra 单测中可能不存在；生产单体同库可查询
        }
    }

    /**
     * 权威内容读取（含私有 path）：仅 FileAccessApi 使用。
     */
    public byte[] getFileContentAllowPrivate(Long configId, String path) throws Exception {
        FileClient client = fileConfigService.getFileClient(configId);
        Assert.notNull(client, "客户端({}) 不能为空", configId);
        return client.getContent(path);
    }

}
