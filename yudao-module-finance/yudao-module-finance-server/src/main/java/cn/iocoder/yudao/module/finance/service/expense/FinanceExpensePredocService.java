package cn.iocoder.yudao.module.finance.service.expense;

public interface FinanceExpensePredocService {

    String TYPE_TRIP = "TRIP";
    String TYPE_OUTING = "OUTING";
    String CAT_TRAVEL = "travel";
    String CAT_TRANSPORT = "transport";

    boolean isApprovedTrip(Long userId, String processInstanceId);

    boolean isApprovedOuting(Long userId, String processInstanceId);

    /** 已通过出差 destination / 外出 location；没有则 null。 */
    String resolveCity(Long userId, String predocType, String processInstanceId);

    StayStay resolveStay(Long userId, String predocType, String processInstanceId);

    Long resolveBillPk(String predocType, String processInstanceId);

    boolean isProcessAssignee(String processInstanceId, Long userId);

    record StayStay(String city, java.time.LocalDate start, java.time.LocalDate end,
                    Long applicantId, java.util.List<Long> companionIds) {
    }
}
