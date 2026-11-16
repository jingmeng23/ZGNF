package com.gas.ecommerce.modules.statistics.vo;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;

public final class StatisticsVO {
    private StatisticsVO() { }
    public record Metrics(long createdOrders,long paidOrders,BigDecimal receivedAmount,
            long refundedOrders,BigDecimal refundAmount,BigDecimal netReceivedAmount,long newCustomers) { }
    public record Overview(LocalDate startDate,LocalDate endDate,LocalDate previousStartDate,LocalDate previousEndDate,
            Metrics current,Metrics previous,BigDecimal receivedGrowthPercent,BigDecimal orderGrowthPercent,
            long pendingWorkOrders,long overdueWorkOrders,Map<String,Long> orderStatuses,List<RecentOrder> recentOrders,
            Todos todos) { }
    public record Todos(long paidOrders,long pendingAftersales,long pendingRefunds) { }
    public record RecentOrder(Long id,String orderNo,String status,BigDecimal payAmount,LocalDateTime createTime) { }
    public record Daily(LocalDate date,Metrics metrics) { }
    public record ProductSales(Long productId,String productName,long paidOrders,long quantity,BigDecimal merchandiseAmount) { }
}
