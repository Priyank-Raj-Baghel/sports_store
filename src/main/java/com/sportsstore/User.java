package com.sportsstore;
import jakarta.persistence.*;
@Entity @Table(name="users")
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public String name; @Column(unique=true) public String email; public String passwordHash; public String role="CUSTOMER";
}
