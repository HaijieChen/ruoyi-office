package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportRespVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 组织架构导入 Service
 */
public interface DeptImportService {

    /**
     * 校验预览：只读，不写库。
     */
    DeptImportRespVO validateImport(MultipartFile file);

    /**
     * 整批原子提交：锁内重新校验后按父到子创建；有错误则零写入。
     *
     * @param expectedDigest 预览阶段返回的 fileDigest，不一致则拒绝
     */
    DeptImportRespVO importDepts(MultipartFile file, String expectedDigest);

}
