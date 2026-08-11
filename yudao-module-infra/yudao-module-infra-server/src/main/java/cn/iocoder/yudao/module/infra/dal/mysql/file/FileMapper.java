package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
     * 通用分页：排除私有目录 + 入职绑定（含软删附件绑定，持久保密）。
     */
    default PageResult<FileDO> selectPagePublic(FilePageReqVO reqVO) {
        QueryWrapper<FileDO> qw = new QueryWrapper<>();
        qw.like(reqVO.getPath() != null, "path", reqVO.getPath())
                .like(reqVO.getType() != null, "type", reqVO.getType());
        if (reqVO.getCreateTime() != null && reqVO.getCreateTime().length == 2) {
            qw.between("create_time", reqVO.getCreateTime()[0], reqVO.getCreateTime()[1]);
        }
        // 排除：私有目录名 或 任意状态（含软删）入职绑定 fileId/path
        qw.notLike("path", "hrm-onboarding-private")
                .apply("NOT EXISTS ("
                        + "SELECT 1 FROM common_attachment a "
                        + "WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding','201') "
                        + "AND (a.file_id = infra_file.id OR a.file_path = infra_file.path)"
                        + ")")
                .orderByDesc("id");
        return selectPage(reqVO, qw);
    }

    /**
     * 字节精确 path 匹配（MySQL BINARY；身份回填用）。
     */
    @Select("SELECT * FROM infra_file WHERE deleted = 0 AND CAST(`path` AS binary) = CAST(#{path} AS binary)")
    List<FileDO> selectListByPathBinary(@Param("path") String path);

    /**
     * 字节精确 url 匹配。
     */
    @Select("SELECT * FROM infra_file WHERE deleted = 0 AND CAST(`url` AS binary) = CAST(#{url} AS binary)")
    List<FileDO> selectListByUrlBinary(@Param("url") String url);

    /**
     * path 是否绑定入职资料/入职单（含软删行，持久保密；守卫宁多勿少，可用普通等值）。
     */
    @Select("SELECT COUNT(1) FROM common_attachment a "
            + "LEFT JOIN infra_file f ON f.id = a.file_id "
            + "WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding', '201') "
            + "AND (a.file_path = #{path} OR f.path = #{path})")
    Long countReservedAttachmentByPath(@Param("path") String path);

    /**
     * fileId 是否绑定入职资料/入职单（含软删）。
     */
    @Select("SELECT COUNT(1) FROM common_attachment a "
            + "WHERE LOWER(TRIM(a.business_type)) IN ('hrm_employee_archive_onboarding', '201') "
            + "AND a.file_id = #{fileId}")
    Long countReservedAttachmentByFileId(@Param("fileId") Long fileId);

}
