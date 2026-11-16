package com.gas.ecommerce.modules.statistics.service;

import com.gas.ecommerce.common.exception.BusinessException;
import com.gas.ecommerce.common.result.PageResult;
import com.gas.ecommerce.modules.statistics.dto.StatisticsQuery;
import com.gas.ecommerce.modules.statistics.vo.StatisticsVO.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class StatisticsService {
    private final JdbcTemplate db;
    public StatisticsService(JdbcTemplate db) {this.db=db;}
    // A refunded payment remains an actual historical receipt. Select one receipt per order.
    private static final String RECEIPTS=" FROM payment p JOIN `order` o ON o.id=p.order_id AND o.is_deleted=0 WHERE p.is_deleted=0 AND p.status IN ('success','refunded') AND p.paid_time IS NOT NULL AND NOT EXISTS (SELECT 1 FROM payment prior WHERE prior.order_id=p.order_id AND prior.is_deleted=0 AND prior.status IN ('success','refunded') AND prior.paid_time IS NOT NULL AND prior.id<p.id)";
    private static final String REFUNDS=" FROM aftersale a JOIN `order` o ON o.id=a.order_id AND o.is_deleted=0 WHERE a.is_deleted=0 AND a.status='completed' AND a.completed_time IS NOT NULL";
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private record Range(LocalDate start,LocalDate end) {
        LocalDateTime from(){return start.atStartOfDay();}
        LocalDateTime to(){return end.plusDays(1).atStartOfDay();}
        long days(){return ChronoUnit.DAYS.between(start,end)+1;}
    }
    private Range range(StatisticsQuery q) {
        LocalDate end=q.getEndDate()==null?LocalDate.now(ZONE):q.getEndDate();
        LocalDate start=q.getStartDate()==null?end:q.getStartDate();
        if(start.isAfter(end)||ChronoUnit.DAYS.between(start,end)>=366||start.isBefore(LocalDate.of(1970,1,1))||end.isAfter(LocalDate.of(9998,12,31)))
            throw new BusinessException("统计日期范围需为1970至9998年内连续1至366天");
        return new Range(start,end);
    }
    public Overview overview(StatisticsQuery q) {
        Range r=range(q);Range previous=new Range(r.start.minusDays(r.days()),r.start.minusDays(1));
        Metrics current=metrics(r),prior=metrics(previous);
        Map<String,Long> statuses=new LinkedHashMap<>();
        for(String s:List.of("pending_payment","paid","processing","completed","cancelled","refunded"))statuses.put(s,0L);
        db.query("SELECT status,COUNT(*) AS n FROM `order` WHERE is_deleted=0 AND create_time>=? AND create_time<? GROUP BY status",rs->{statuses.put(rs.getString("status"),rs.getLong("n"));},r.from(),r.to());
        LocalDateTime now=LocalDateTime.now(ZONE);
        long open=count("SELECT COUNT(*) FROM work_order WHERE is_deleted=0 AND status IN ('pending','accepted','processing')");
        long overdue=count("SELECT COUNT(*) FROM work_order WHERE is_deleted=0 AND status IN ('pending','accepted','processing') AND appointment_time<?",now);
        var recent=db.query("SELECT id,order_no,status,pay_amount,create_time FROM `order` WHERE is_deleted=0 AND create_time>=? AND create_time<? ORDER BY create_time DESC,id DESC LIMIT 10",(rs,n)->new RecentOrder(rs.getLong("id"),rs.getString("order_no"),rs.getString("status"),rs.getBigDecimal("pay_amount"),rs.getTimestamp("create_time").toLocalDateTime()),r.from(),r.to());
        Todos todos=new Todos(
                count("SELECT COUNT(*) FROM `order` WHERE is_deleted=0 AND status='paid'"),
                count("SELECT COUNT(*) FROM aftersale WHERE is_deleted=0 AND status='pending'"),
                count("SELECT COUNT(*) FROM aftersale WHERE is_deleted=0 AND status='approved'"));
        return new Overview(r.start,r.end,previous.start,previous.end,current,prior,growth(current.receivedAmount(),prior.receivedAmount()),growth(BigDecimal.valueOf(current.createdOrders()),BigDecimal.valueOf(prior.createdOrders())),open,overdue,statuses,recent,todos);
    }
    private Metrics metrics(Range r) {
        long orders=count("SELECT COUNT(*) FROM `order` WHERE is_deleted=0 AND create_time>=? AND create_time<?",r.from(),r.to());
        long users=count("SELECT COUNT(*) FROM `user` WHERE is_deleted=0 AND create_time>=? AND create_time<?",r.from(),r.to());
        var receipts=db.queryForMap("SELECT COUNT(*) AS n,COALESCE(SUM(p.amount),0) AS amount"+RECEIPTS+" AND p.paid_time>=? AND p.paid_time<?",r.from(),r.to());
        var refunds=db.queryForMap("SELECT COUNT(DISTINCT a.order_id) AS n,COALESCE(SUM(a.amount),0) AS amount"+REFUNDS+" AND a.completed_time>=? AND a.completed_time<?",r.from(),r.to());
        BigDecimal received=new BigDecimal(receipts.get("amount").toString()).setScale(2),refund=new BigDecimal(refunds.get("amount").toString()).setScale(2);
        return new Metrics(orders,((Number)receipts.get("n")).longValue(),received,((Number)refunds.get("n")).longValue(),refund,received.subtract(refund),users);
    }
    private long count(String sql,Object... args){return db.queryForObject(sql,Long.class,args);}
    private BigDecimal growth(BigDecimal current,BigDecimal previous) {return previous.signum()==0?null:current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous,2,RoundingMode.HALF_UP);}

    public List<Daily> trend(StatisticsQuery q) {
        Range r=range(q);
        Map<LocalDate,Long> orders=dailyCounts("SELECT CAST(create_time AS DATE) AS report_date,COUNT(*) AS n FROM `order` WHERE is_deleted=0 AND create_time>=? AND create_time<? GROUP BY CAST(create_time AS DATE)",r);
        Map<LocalDate,Long> customers=dailyCounts("SELECT CAST(create_time AS DATE) AS report_date,COUNT(*) AS n FROM `user` WHERE is_deleted=0 AND create_time>=? AND create_time<? GROUP BY CAST(create_time AS DATE)",r);
        Map<LocalDate,Amount> receipts=dailyAmounts("SELECT CAST(p.paid_time AS DATE) AS report_date,COUNT(*) AS n,SUM(p.amount) AS amount"+RECEIPTS+" AND p.paid_time>=? AND p.paid_time<? GROUP BY CAST(p.paid_time AS DATE)",r);
        Map<LocalDate,Amount> refunds=dailyAmounts("SELECT CAST(a.completed_time AS DATE) AS report_date,COUNT(DISTINCT a.order_id) AS n,SUM(a.amount) AS amount"+REFUNDS+" AND a.completed_time>=? AND a.completed_time<? GROUP BY CAST(a.completed_time AS DATE)",r);
        List<Daily> result=new ArrayList<>();
        for(LocalDate d=r.start;!d.isAfter(r.end);d=d.plusDays(1)) {
            Amount paid=receipts.getOrDefault(d,Amount.ZERO),refund=refunds.getOrDefault(d,Amount.ZERO);
            result.add(new Daily(d,new Metrics(orders.getOrDefault(d,0L),paid.count,paid.amount,refund.count,refund.amount,paid.amount.subtract(refund.amount),customers.getOrDefault(d,0L))));
        }
        return result;
    }
    private record Amount(long count,BigDecimal amount) {static final Amount ZERO=new Amount(0,new BigDecimal("0.00"));}
    private Map<LocalDate,Long> dailyCounts(String sql,Range r) {
        Map<LocalDate,Long> result=new HashMap<>();db.query(sql,rs->{result.put(rs.getDate("report_date").toLocalDate(),rs.getLong("n"));},r.from(),r.to());return result;
    }
    private Map<LocalDate,Amount> dailyAmounts(String sql,Range r) {
        Map<LocalDate,Amount> result=new HashMap<>();db.query(sql,rs->{result.put(rs.getDate("report_date").toLocalDate(),new Amount(rs.getLong("n"),rs.getBigDecimal("amount").setScale(2)));},r.from(),r.to());return result;
    }
    public PageResult<Daily> dailyReport(StatisticsQuery q) {
        q.resolveSort(Map.of("createTime","date","date","date"));
        List<Daily> all=new ArrayList<>(trend(q));if(!"asc".equals(q.getOrder()))Collections.reverse(all);
        long offset=((long)q.getPage()-1)*q.getSize();int from=(int)Math.min(offset,all.size()),to=Math.min(from+q.getSize(),all.size());
        return new PageResult<>(all.subList(from,to),all.size(),q.getPage(),q.getSize(),(all.size()+q.getSize()-1)/q.getSize());
    }
    public List<ProductSales> products(StatisticsQuery q) {
        Range r=range(q);
        // EXISTS avoids multiplying item quantities when payment history has multiple rows.
        return db.query("SELECT i.product_id,MAX(i.product_name) AS name,COUNT(DISTINCT i.order_id) AS orders,SUM(i.quantity) AS quantity,SUM(i.amount) AS amount FROM order_item i JOIN `order` o ON o.id=i.order_id AND o.is_deleted=0 WHERE i.is_deleted=0 AND EXISTS (SELECT 1"+RECEIPTS+" AND p.order_id=i.order_id AND p.paid_time>=? AND p.paid_time<?) GROUP BY i.product_id ORDER BY quantity DESC,i.product_id ASC LIMIT ?",
                (rs,n)->new ProductSales(rs.getLong("product_id"),rs.getString("name"),rs.getLong("orders"),rs.getLong("quantity"),rs.getBigDecimal("amount")),r.from(),r.to(),q.getLimit());
    }
    public String exportDaily(StatisticsQuery q) {
        StringBuilder csv=new StringBuilder("\uFEFFdate,created_orders,paid_orders,received_amount,refunded_orders,refund_amount,net_received_amount,new_customers\r\n");
        for(Daily day:trend(q)) {Metrics m=day.metrics();csv.append(day.date()).append(',').append(m.createdOrders()).append(',').append(m.paidOrders()).append(',').append(m.receivedAmount()).append(',').append(m.refundedOrders()).append(',').append(m.refundAmount()).append(',').append(m.netReceivedAmount()).append(',').append(m.newCustomers()).append("\r\n");}
        return csv.toString();
    }
}
