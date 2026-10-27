package com.gas.ecommerce.modules.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.modules.cart.entity.CartItem;
import com.gas.ecommerce.modules.cart.mapper.CartItemMapper;
import com.gas.ecommerce.modules.marketing.service.CheckoutMarketingService;
import com.gas.ecommerce.modules.product.entity.Product;
import com.gas.ecommerce.modules.product.service.ProductCheckoutService;
import com.gas.ecommerce.modules.user.entity.UserAddress;
import com.gas.ecommerce.modules.user.service.AddressCheckoutService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckoutSettlementServiceTests {
    CartItemMapper cart=mock(CartItemMapper.class);
    ProductCheckoutService products=mock(ProductCheckoutService.class);
    CheckoutMarketingService marketing=mock(CheckoutMarketingService.class);
    AddressCheckoutService addresses=mock(AddressCheckoutService.class);
    CheckoutSettlementService service;
    @BeforeEach void setup() {
        service=new CheckoutSettlementService(cart,products,marketing,addresses,new ObjectMapper().findAndRegisterModules());
        CartItem line=new CartItem(); line.setId(1L); line.setProductId(20L); line.setQuantity(2);
        when(cart.selectList(any())).thenReturn(List.of(line));
        Product product=new Product(); product.setId(20L); product.setPrice(new BigDecimal("50.00"));
        product.setName("product");
        when(products.quote(any())).thenReturn(Map.of(20L,new ProductCheckoutService.Quote(product,10)));
        when(addresses.quote(1L,5L)).thenReturn(new AddressCheckoutService.Quote(new UserAddress(),new BigDecimal("7.50"),true));
        when(marketing.quote(1L,null,new BigDecimal("100.00"))).thenReturn(new CheckoutMarketingService.Quote(new BigDecimal("10.00"),new BigDecimal("20.00")));
    }
    @Test void amountIsProductSubtotalMinusMarketingPlusAuthoritativeFreight() {
        var quote=service.settle(1L,5L,null);
        assertEquals(0,new BigDecimal("77.50").compareTo(quote.payAmount()));
        assertEquals(0,new BigDecimal("70.00").compareTo(quote.items().getFirst().getPayAmount()));
    }
    @Test void productFailureCannotUseOldCartPrices() {
        when(products.quote(any())).thenThrow(new IllegalStateException("timeout"));
        assertThrows(CheckoutUnavailableException.class,()->service.settle(1L,5L,null));
        verifyNoInteractions(marketing,addresses);
    }
    @Test void addressFailureCannotSilentlyTurnIntoFreeShipping() {
        when(addresses.quote(1L,5L)).thenThrow(new IllegalStateException("timeout"));
        assertThrows(CheckoutUnavailableException.class,()->service.settle(1L,5L,null));
        verifyNoInteractions(marketing);
    }
    @Test void marketingFailureCannotSilentlyRemoveUserDiscounts() {
        when(marketing.quote(anyLong(),isNull(),any())).thenThrow(new IllegalStateException("timeout"));
        assertThrows(CheckoutUnavailableException.class,()->service.settle(1L,5L,null));
    }
}
