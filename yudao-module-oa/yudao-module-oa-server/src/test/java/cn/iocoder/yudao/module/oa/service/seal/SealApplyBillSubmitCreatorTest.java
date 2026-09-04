package cn.iocoder.yudao.module.oa.service.seal;

import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.oa.controller.admin.seal.vo.SealApplyBillSaveReqVO;
import cn.iocoder.yudao.module.oa.dal.dataobject.seal.SealApplyBillDO;
import cn.iocoder.yudao.module.oa.dal.mysql.seal.SealApplyBillMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一发起表单只传 creatorName、不传 creator 时，须用登录用户 ID 发起流程，
 * 不能 Long.valueOf(null) 打成系统异常。
 */
@ExtendWith(MockitoExtension.class)
class SealApplyBillSubmitCreatorTest {

    @InjectMocks
    private SealApplyBillServiceImpl service;

    @Mock
    private SealApplyBillMapper sealApplyBillMapper;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private BpmProcessInstanceApi processInstanceApi;

    @Test
    void submit_withoutCreator_usesLoginUserId() {
        SealApplyBillSaveReqVO req = new SealApplyBillSaveReqVO();
        req.setBillCode("OA103-2026090400001");
        req.setSealName("北京三鼎梦软件服务有限公司");
        req.setCause("用于网站运营授权");
        req.setUseType(1);
        req.setUseMode(1);
        req.setDocumentCount(1);
        req.setCreatorName("何雨舟");
        req.setCompanyId(369L);
        req.setCompanyName("上海文枢网络科技有限公司");
        req.setDeptId(467L);
        req.setDeptName("品牌商务组");

        doAnswer(inv -> {
            SealApplyBillDO bill = inv.getArgument(0);
            bill.setId(10L);
            return true;
        }).when(sealApplyBillMapper).insertOrUpdate(any(SealApplyBillDO.class));

        when(processInstanceApi.submitProcessInstance(eq(812L), any()))
                .thenReturn(CommonResult.success("proc-1"));

        try (MockedStatic<SecurityFrameworkUtils> sec = mockStatic(SecurityFrameworkUtils.class)) {
            sec.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(812L);
            assertEquals(10L, service.submitSealApplyBill(req));
        }

        verify(processInstanceApi).submitProcessInstance(eq(812L), any());
    }
}
