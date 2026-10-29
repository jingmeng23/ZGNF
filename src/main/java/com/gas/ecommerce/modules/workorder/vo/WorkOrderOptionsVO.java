package com.gas.ecommerce.modules.workorder.vo;
import java.util.List;
public record WorkOrderOptionsVO(List<StoreOption> stores,List<EmployeeOption> employees) {
    public record StoreOption(Long id,String name) { }
    public record EmployeeOption(Long id,String name,Long storeId) { }
}
