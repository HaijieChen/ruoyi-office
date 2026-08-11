package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文件操作 Mapper
 *
 * @author 宇擎源码
 */
@Mapper
public interface FileMapper extends BaseMapperX<FileDO> {

    default PageResult<FileDO> selectPage(FilePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FileDO>()
                .likeIfPresent(FileDO::getPath, reqVO.getPath())
                .likeIfPresent(FileDO::getType, reqVO.getType())
                .betweenIfPresent(FileDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(FileDO::getId));
    }

    /**
     * 通用分页：排除入职资料私有目录。
     */
    default PageResult<FileDO> selectPagePublic(FilePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FileDO>()
                .likeIfPresent(FileDO::getPath, reqVO.getPath())
                .likeIfPresent(FileDO::getType, reqVO.getType())
                .betweenIfPresent(FileDO::getCreateTime, reqVO.getCreateTime())
                .notLike(FileDO::getPath, "hrm-onboarding-private")
                .orderByDesc(FileDO::getId));
    }

    /**
     * 字节精确 path 匹配（BINARY，避免 utf8mb4_unicode_ci 大小写折叠）。
     */
    @Select("SELECT * FROM infra_file WHERE deleted = 0 AND BINARY `path` = BINARY #{path}")
    List<FileDO> selectListByPathBinary(@Param("path") String path);

    /**
     * 字节精确 url 匹配。
     */
    @Select("SELECT * FROM infra_file WHERE deleted = 0 AND BINARY `url` = BINARY #{url}")
    List<FileDO> selectListByUrlBinary(@Param("url") String url);

    /**
     * path 是否绑定入职资料/入职单附件（含大小写别名），用于匿名下载拒绝。
     */
    @Select("SELECT COUNT(1) FROM common_attachment a "
            + "LEFT JOIN infra_file f ON f.id = a.file_id AND f.deleted = 0 "
            + "WHERE a.deleted = 0 "
            + "AND LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding', '201') "
            + "AND (BINARY a.file_path = BINARY #{path} OR BINARY f.path = BINARY #{path})")
    Long countReservedAttachmentByPath(@Param("path") String path);

}
