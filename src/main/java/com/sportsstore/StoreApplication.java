package com.sportsstore;
import org.springframework.boot.*; import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@SpringBootApplication
public class StoreApplication {
  public static void main(String[] a){ SpringApplication.run(StoreApplication.class,a); }
  @Bean BCryptPasswordEncoder encoder(){ return new BCryptPasswordEncoder(); }
  static User user(String n,String e,String p,String r,BCryptPasswordEncoder enc){ User u=new User(); u.name=n; u.email=e; u.passwordHash=enc.encode(p); u.role=r; return u; }
  @Bean CommandLineRunner seed(ProductRepo pr,VariantRepo vr,UserRepo ur,BCryptPasswordEncoder enc){ return x->{
    if(ur.count()>0) return;
    ur.save(user("Admin","admin@store.com","admin123","ADMIN",enc)); ur.save(user("Demo Customer","demo@store.com","demo123","CUSTOMER",enc));
    String[] rows={
     "Pro Run Tee|Men|Running|899|10|👕|Lightweight breathable running t-shirt with quick dry fabric",
     "Speed Run Shorts|Men|Running|699|0|🩳|Light running shorts with zip pocket and quick dry fabric",
     "Flex Gym Tank|Men|Gym|599|15|🎽|Sleeveless gym training tank, sweat wicking and breathable",
     "PowerFit Joggers|Men|Gym|1299|20|👖|Tapered gym joggers with stretch fabric for training",
     "Striker Football Jersey|Men|Football|999|0|⚽|Football match jersey in breathable mesh",
     "Willow Cricket Tee|Men|Cricket|1099|5|🏏|Cricket practice tee, moisture wicking and breathable",
     "Zen Yoga Leggings|Women|Yoga|1499|10|🧘|High waist stretch yoga leggings, soft fabric",
     "Aura Gym Sports Bra|Women|Gym|799|0|🏋|Medium support gym sports bra, sweat wicking",
     "Stride Running Tights|Women|Running|1399|25|🏃|Compression running tights for long runs, quick dry",
     "Lotus Yoga Top|Women|Yoga|899|0|🧘|Breathable yoga top, relaxed soft fit",
     "Junior Football Jersey|Kids|Football|649|0|⚽|Kids football jersey, durable and soft mesh",
     "Mini Runner Tee|Kids|Running|449|10|🏃|Kids running t-shirt, light and quick dry"};
    int i=0;
    for(String r:rows){ String[] f=r.split("\\|"); Product p=new Product();
      p.name=f[0]; p.category=f[1]; p.sportType=f[2]; p.price=Double.parseDouble(f[3]); p.discount=Integer.parseInt(f[4]); p.emoji=f[5]; p.description=f[6]; pr.save(p);
      int j=0; for(String c:new String[]{"Black","Blue"}) for(String s:new String[]{"S","M","L","XL"}){
        Variant v=new Variant(); v.product=p; v.colour=c; v.size=s; v.sku="SKU"+p.id+"-"+c.substring(0,3).toUpperCase()+"-"+s; v.stock=3+(i*3+j++)%8; vr.save(v); }
      i++; }
  }; }
}
