package cn.iocoder.yudao.module.hrm.service.employee;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeePageReqVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeRespVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeRosterExportVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeRosterImportExcelVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeRosterImportRespVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeSelectPageReqVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeSaveReqVO;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.OnboardingFileClaimRespVO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 员工档案 Service 接口
 *
 * @author 宇擎源码
 */
public interface EmployeeService {

    /**
     * 创建员工档案
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createEmployeeArchive(@Valid EmployeeSaveReqVO createReqVO);

    java.util.List<cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeEmploymentVO> listMyEmployments(Long userId);

    /**
     * 更新员工档案
     *
     * @param updateReqVO 更新信息
     */
    void updateEmployeeArchive(@Valid EmployeeSaveReqVO updateReqVO);

    /**
     * 删除员工档案
     *
     * @param id 编号
     */
    void deleteEmployeeArchive(Long id);

    /**
     * 批量删除员工档案
     *
     * @param ids 编号列表
     */
    void deleteEmployeeArchiveList(List<Long> ids);

    /**
     * 获得员工档案
     *
     * @param id 编号
     * @return 员工档案
     */
    EmployeeRespVO getEmployeeArchive(Long id);

    /**
     * 获得员工档案分页
     *
     * @param pageReqVO 分页查询
     * @return 员工档案分页
     */
    PageResult<EmployeeRespVO> getEmployeeArchivePage(EmployeePageReqVO pageReqVO);

    /**
     * 获得可选择的员工档案分页（过滤正式员工）
     *
     * @param pageReqVO 分页查询
     * @return 员工档案分页
     */
    PageResult<EmployeeRespVO> getEmployeeArchiveSelectablePage(EmployeeSelectPageReqVO pageReqVO);

    /**
     * 为员工生成系统用户
     *
     * @param employeeId 员工编号
     * @return 生成的用户编号
     */
    Long generateUserForEmployee(Long employeeId);

    /**
     * 批量为员工生成系统用户
     *
     * @param employeeIds 员工编号列表
     * @return 生成结果（成功数量、失败数量）
     */
    void batchGenerateUserForEmployee(List<Long> employeeIds);

    /**
     * 获得文枢花名册导出列表（52 列）
     *
     * @param pageReqVO 查询条件
     * @return 导出列表
     */
    List<EmployeeRosterExportVO> getEmployeeRosterExportList(EmployeePageReqVO pageReqVO);

    /**
     * 批量导入文枢花名册（按身份证号 upsert，逐行部分成功）
     *
     * @param rows 已解析的 Excel 数据行（不含表头）
     * @return 成功/失败明细
     */
    EmployeeRosterImportRespVO importEmployeeRosterList(List<EmployeeRosterImportExcelVO> rows);

    /**
     * 鉴权下载入职资料附件
     */
    void downloadOnboardingAttachment(Long employeeId, Long attachmentId,
                                      jakarta.servlet.http.HttpServletResponse response) throws Exception;

    /**
     * 上传入职资料并签发一次性 claim（不返回公开 URL）
     */
    OnboardingFileClaimRespVO uploadOnboardingFile(org.springframework.web.multipart.MultipartFile file)
            throws Exception;

}

