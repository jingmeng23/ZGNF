package com.gas.ecommerce.modules.statistics.controller;

import com.gas.ecommerce.common.annotation.RequiresPermission;
import com.gas.ecommerce.common.result.*;
import com.gas.ecommerce.modules.statistics.dto.StatisticsQuery;
import com.gas.ecommerce.modules.statistics.service.StatisticsService;
import com.gas.ecommerce.modules.statistics.vo.StatisticsVO.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/statistics")
public class AdminStatisticsController {
    private final StatisticsService service;
    public AdminStatisticsController(StatisticsService service) {this.service=service;}
    @GetMapping("/overview") @RequiresPermission("statistics:read")
    public Result<Overview> overview(@Valid @ModelAttribute StatisticsQuery q){return Result.success(service.overview(q));}
    @GetMapping("/trend") @RequiresPermission("statistics:read")
    public Result<List<Daily>> trend(@Valid @ModelAttribute StatisticsQuery q){return Result.success(service.trend(q));}
    @GetMapping("/products") @RequiresPermission("statistics:read")
    public Result<List<ProductSales>> products(@Valid @ModelAttribute StatisticsQuery q){return Result.success(service.products(q));}
    @GetMapping("/reports/daily") @RequiresPermission("statistics:read")
    public Result<PageResult<Daily>> daily(@Valid @ModelAttribute StatisticsQuery q){return Result.success(service.dailyReport(q));}
    @GetMapping("/reports/daily/export") @RequiresPermission("statistics:export")
    public ResponseEntity<String> export(@Valid @ModelAttribute StatisticsQuery q){return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=business-daily.csv")
            .body(service.exportDaily(q));}
}
