package hy.CommunityBuy;
import hy.CommunityBuy.commerce.*;
import hy.CommunityBuy.commerce.CommerceDtos.*;
import hy.CommunityBuy.dto.Dtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(IntegrationSupport.TimeConfig.class)
class CommerceIntegrationTest extends IntegrationSupport {
    @Autowired ShopService shop;
    @Autowired OrderLines lines;
    Checkout checkout(long version,Long total){return new Checkout("Checkout address",version,total);}
    @Test void crossSellerCheckoutGroupsItemsAndConsumesOnlySelectedCart() {
        long first=item(5).id(),second=item(5).id();
        long third=itemService.create(outsider,new NewItem("Bread",null,300,5,0,0L,null)).id();
        long unselected=item(5).id();
        shop.cartChange(buyer,first,new CartChange(2,true));shop.cartChange(buyer,second,new CartChange(1,true));
        shop.cartChange(buyer,third,new CartChange(1,true));var cart=shop.cartChange(buyer,unselected,new CartChange(1,false));
        var batch=shop.checkout(buyer,checkout(cart.version(),897L),"cross-seller");
        assertThat(batch.orders()).hasSize(2);assertThat(batch.total()).isEqualTo(897);
        assertThat(batch.orders().stream().mapToLong(o->o.order().total()).sum()).isEqualTo(897);
        var sellerOrder=batch.orders().stream().filter(o->o.order().owner().equals("seller")).findFirst().orElseThrow();
        assertThat(sellerOrder.lines()).hasSize(2);assertThat(sellerOrder.order().quantity()).isEqualTo(3);
        assertThat(shop.cart(buyer).lines()).extracting(CartLine::itemId).containsExactly(unselected);
        orderService.action(buyer,sellerOrder.order().id(),"cancel");
        assertThat(items.findById(first).orElseThrow().getStock()).isEqualTo(5);assertThat(items.findById(second).orElseThrow().getStock()).isEqualTo(5);
    }
    @Test void checkoutRetriesArePersistentAndAddressOrVersionConflictsReject() throws Exception {
        long item=item(3).id();var cart=shop.cartChange(buyer,item,new CartChange(1,true));var request=checkout(cart.version(),199L);
        List<CheckoutView> results=race(List.of(()->shop.checkout(buyer,request,"retry"),()->shop.checkout(buyer,request,"retry")));
        assertThat(results.get(0).id()).isEqualTo(results.get(1).id());assertThat(orders.count()).isEqualTo(1);
        assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(2);
        assertThat(shop.checkout(buyer,request,"retry").id()).isEqualTo(results.getFirst().id());
        assertThatThrownBy(()->shop.checkout(buyer,new Checkout("Changed",cart.version(),199L),"retry")).hasMessageContaining("differ");
    }
    @Test void checkoutRechecksPriceAndRejectsStaleCartWithoutChangingInventory() {
        long item=item(3).id();var cart=shop.cartChange(buyer,item,new CartChange(1,true));
        itemService.update(seller,item,new EditItem(null,null,299,null,null,null,null,null));
        assertThatThrownBy(()->shop.checkout(buyer,checkout(cart.version(),199L),"old-price")).hasMessageContaining("Price changed");
        assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(3);assertThat(shop.cart(buyer).lines()).hasSize(1);
        var changed=shop.cartChange(buyer,item,new CartChange(2,true));
        assertThatThrownBy(()->shop.checkout(buyer,checkout(cart.version(),null),"old-cart")).hasMessageContaining("Cart changed");
        var batch=shop.checkout(buyer,checkout(changed.version(),598L),"confirmed");assertThat(batch.total()).isEqualTo(598);
    }
    @Test void unavailableOneSellerRollsBackTheWholeCheckout() {
        long first=item(2).id();long second=itemService.create(outsider,new NewItem("Other",null,10,0,0,0L,null)).id();
        shop.cartChange(buyer,first,new CartChange(1,true));var cart=shop.cartChange(buyer,second,new CartChange(1,true));
        assertThatThrownBy(()->shop.checkout(buyer,checkout(cart.version(),null),"fail-all")).hasMessageContaining("stock");
        assertThat(orders.count()).isZero();assertThat(items.findById(first).orElseThrow().getStock()).isEqualTo(2);assertThat(shop.cart(buyer).lines()).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from checkout_batches",Long.class)).isZero();
    }
    @Test void secondOrderInsertFailureRollsBackBothSellersAndCart() {
        long first=item(2).id(),second=itemService.create(outsider,new NewItem("Other",null,10,2,0,0L,null)).id();
        shop.cartChange(buyer,first,new CartChange(1,true));var cart=shop.cartChange(buyer,second,new CartChange(1,true));
        jdbc.execute("ALTER TABLE orders ADD CONSTRAINT ck_second_seller_failure CHECK(owner_id<>'seller')");
        try{assertThatThrownBy(()->shop.checkout(buyer,checkout(cart.version(),null),"db-fail-all")).isInstanceOf(org.springframework.dao.DataAccessException.class);}
        finally{jdbc.execute("ALTER TABLE orders DROP CHECK ck_second_seller_failure");}
        assertThat(orders.count()).isZero();assertThat(items.findById(first).orElseThrow().getStock()).isEqualTo(2);assertThat(items.findById(second).orElseThrow().getStock()).isEqualTo(2);
        assertThat(shop.cart(buyer).lines()).hasSize(2);assertThat(jdbc.queryForObject("select count(*) from checkout_batches",Long.class)).isZero();
    }
    @Test void orderQueriesIncludeNonPrimaryLineAndStillEnforceScope() {
        long first=item(2).id(),second=item(2).id();shop.cartChange(buyer,first,new CartChange(1,true));var cart=shop.cartChange(buyer,second,new CartChange(1,true));
        var batch=shop.checkout(buyer,checkout(cart.version(),null),"lines");
        assertThat(orderService.list(buyer,null,null,second,0,0,20).count()).isEqualTo(1);
        assertThat(orderService.list(outsider,"buyer",null,second,0,0,20).count()).isZero();
        assertThatThrownBy(()->shop.batch(seller,batch.id())).hasMessageContaining("denied");
        assertThat(shop.details(seller,batch.orders().getFirst().order().id()).order().address()).isNull();
    }
    @Test void categoryAndKeywordOwnerStatusFiltersAllApply() throws Exception {
        var category=shop.category(administrator,new CategoryRequest("Fruit"));long first=item(2).id();item(2);
        shop.assign(seller,first,category.id());
        request("buyer","GET","/api/v1/item?category="+category.id()+"&keyword=Apples&owner=seller&status=0&size=1",null)
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(1));
        request("buyer","POST","/api/v1/categories",new CategoryRequest("No permission")).andExpect(status().isForbidden());
        assertThatThrownBy(()->shop.assign(buyer,first,category.id())).hasMessageContaining("denied");
    }
    @Test void cartHttpIsPrivateAndValidatesQuantityAndCheckoutParameters() throws Exception {
        long id=item(2).id();
        mvc.perform(put("/api/v1/cart/"+id).header("Authorization","Bearer "+token("buyer")).contentType("application/json").content("{\"quantity\":0,\"selected\":true}"))
            .andExpect(status().isBadRequest());
        shop.cartChange(buyer,id,new CartChange(1,true));assertThat(shop.cart(outsider).lines()).isEmpty();
        mvc.perform(delete("/api/v1/cart/"+id).header("Authorization","Bearer "+token("outsider"))).andExpect(status().isOk());
        assertThat(shop.cart(buyer).lines()).hasSize(1);
        request("buyer","POST","/api/v1/checkout",Map.of("address","bad", "cartVersion",0,"price",1)).andExpect(status().isBadRequest());
    }
}
