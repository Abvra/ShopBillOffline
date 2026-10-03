package com.abvra.shopbilloffline;

public class CartItem {
    long id; String code; String name; double price; double qty=1; double discount=0;
    public CartItem(long id,String code,String name,double price){this.id=id;this.code=code;this.name=name;this.price=price;}
    public double lineGross(){return price*qty;}
    public double lineDiscount(){return lineGross()*(discount/100.0);}
    public double lineTotal(){return Math.max(0,lineGross()-lineDiscount());}
}
