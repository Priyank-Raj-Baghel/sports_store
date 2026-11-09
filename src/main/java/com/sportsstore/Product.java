package com.sportsstore;
import jakarta.persistence.*; import java.util.*;
@Entity
public class Product {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public String name, category, sportType, emoji; @Column(length=500) public String description;
  public double price; public int discount;
  @OneToMany(mappedBy="product",cascade=CascadeType.ALL) public List<Variant> variants=new ArrayList<>();
  public double finalPrice(){ return Math.round(price*(100-discount)/100.0); }
}
