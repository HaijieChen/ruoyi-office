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
}
