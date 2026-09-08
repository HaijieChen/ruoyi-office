package cn.iocoder.yudao.module.bpm.service.oa;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOALeavePageReqVO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOALeaveMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class BpmLeaveReportScopeTest {
 BpmOALeaveServiceImpl service; BpmOALeaveMapper mapper; PermissionApi permissions; AdminUserApi users;
 BpmOALeavePageReqVO query=new BpmOALeavePageReqVO();
 @BeforeEach void setup(){
  service=new BpmOALeaveServiceImpl();mapper=mock(BpmOALeaveMapper.class);permissions=mock(PermissionApi.class);users=mock(AdminUserApi.class);
  ReflectionTestUtils.setField(service,"leaveMapper",mapper);ReflectionTestUtils.setField(service,"permissionApi",permissions);ReflectionTestUtils.setField(service,"adminUserApi",users);
 }
 void scope(DeptDataPermissionRespDTO s){when(permissions.getDeptDataPermission(1L)).thenReturn(CommonResult.success(s));}
 @Test void allScopeRemovesOwnerRestriction(){scope(new DeptDataPermissionRespDTO().setAll(true));service.getLeaveReportPage(1L,query);verify(mapper).selectPageByUsers(null,query);verifyNoInteractions(users);}
 @Test void selfOnly(){scope(new DeptDataPermissionRespDTO().setSelf(true));service.getLeaveReportPage(1L,query);verify(mapper).selectPageByUsers(Set.of(1L),query);verifyNoInteractions(users);}
 @Test void departmentUnionSelf(){scope(new DeptDataPermissionRespDTO().setSelf(true).setDeptIds(Set.of(397L)));when(users.getUserListByDeptIds(Set.of(397L))).thenReturn(CommonResult.success(List.of(new AdminUserRespDTO().setId(710L))));service.getLeaveReportPage(1L,query);verify(mapper).selectPageByUsers(Set.of(1L,710L),query);}
 @Test void departmentWithoutSelf(){scope(new DeptDataPermissionRespDTO().setDeptIds(Set.of(397L)));when(users.getUserListByDeptIds(Set.of(397L))).thenReturn(CommonResult.success(List.of(new AdminUserRespDTO().setId(710L))));service.getLeaveReportPage(1L,query);verify(mapper).selectPageByUsers(Set.of(710L),query);}
 @Test void emptyDepartmentDenied(){scope(new DeptDataPermissionRespDTO().setDeptIds(Set.of(397L)));when(users.getUserListByDeptIds(Set.of(397L))).thenReturn(CommonResult.success(List.of()));service.getLeaveReportPage(1L,query);verify(mapper).selectPageByUsers(Set.of(),query);}
 @Test void emptyScopeDenied(){scope(new DeptDataPermissionRespDTO());service.getLeaveReportPage(1L,query);verify(mapper).selectPageByUsers(Set.of(),query);}
 @Test void missingScopeDenied(){scope(null);service.getLeaveReportPage(1L,query);verify(mapper).selectPageByUsers(Set.of(),query);}
 @Test void mineStillOwnerOnly(){service.getLeavePage(1L,query);verify(mapper).selectPage(1L,query);verifyNoInteractions(permissions,users);}
 @Test void noLoginRejected(){assertThrows(NullPointerException.class,()->service.getLeaveReportPage(null,query));verifyNoInteractions(mapper,permissions);}
 @Test void permissionFailurePropagates(){when(permissions.getDeptDataPermission(1L)).thenThrow(new IllegalStateException("unavailable"));assertThrows(IllegalStateException.class,()->service.getLeaveReportPage(1L,query));verifyNoInteractions(mapper);}
}
