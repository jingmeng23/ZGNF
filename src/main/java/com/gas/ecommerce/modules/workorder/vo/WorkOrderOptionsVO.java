package com.gas.ecommerce.modules.workorder.vo;
import java.util.List;
public record WorkOrderOptionsVO(List<EmployeeOption> employees) {
    public record EmployeeOption(Long id,String name) { }
}
