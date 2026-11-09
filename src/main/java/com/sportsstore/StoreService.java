package com.sportsstore;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.*; import java.util.stream.*;
import java.net.URI; import java.net.http.*; import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {
  public static class Line { public Variant v; public int qty; public double total; }
  private final ProductRepo products; private final VariantRepo variants; private final OrderRepo orders;
  @Value("${payment.secret}") String secret;
  @Value("${razorpay.key-id:}") String keyId; @Value("${razorpay.key-secret:}") String keySecret;
  /** true when Razorpay keys are configured; otherwise the local mock gateway is used */
  public boolean live(){ return !keyId.isBlank()&&!keySecret.isBlank(); }

  /** Creates an order on Razorpay (server-to-server, Basic auth) and returns its id. */
  public String createGatewayOrder(PurchaseOrder o){
    try{
      String body="{\"amount\":"+Math.round(o.total*100)+",\"currency\":\"INR\",\"receipt\":\"rcpt_"+o.id+"\"}";
      String auth=Base64.getEncoder().encodeToString((keyId+":"+keySecret).getBytes(StandardCharsets.UTF_8));
      HttpRequest rq=HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/orders")).header("Content-Type","application/json")
        .header("Authorization","Basic "+auth).POST(HttpRequest.BodyPublishers.ofString(body)).build();
      HttpResponse<String> rs=HttpClient.newHttpClient().send(rq,HttpResponse.BodyHandlers.ofString());
      if(rs.statusCode()!=200) throw new IllegalStateException("Razorpay error: "+rs.body());
      return new ObjectMapper().readTree(rs.body()).get("id").asText();
    }catch(IllegalStateException e){ throw e; }
    catch(Exception e){ throw new IllegalStateException("Could not reach Razorpay: "+e.getMessage()); } }

  /** Called when the pay page opens: in live mode the Razorpay order id becomes the order's paymentRef. */
  public PurchaseOrder startPayment(PurchaseOrder o){
    if(live()){ o.paymentRef=createGatewayOrder(o); orders.save(o); }
    return o; }
  public StoreService(ProductRepo p,VariantRepo v,OrderRepo o){ products=p; variants=v; orders=o; }

  public List<Line> lines(Map<Long,Integer> cart){
    List<Line> out=new ArrayList<>();
    for(var e:cart.entrySet()) variants.findById(e.getKey()).ifPresent(v->{ Line l=new Line(); l.v=v; l.qty=e.getValue(); l.total=v.product.finalPrice()*l.qty; out.add(l); });
    return out; }
  public double delivery(double sub){ return sub==0||sub>=1999?0:99; }   // free delivery above Rs 1999

  /** Validates stock under row locks, recalculates prices from DB (never trusts browser), creates the order. */
  @Transactional
  public PurchaseOrder placeOrder(User u,Map<Long,Integer> cart,String address,String method){
    if(cart.isEmpty()) throw new IllegalStateException("Cart is empty");
    PurchaseOrder o=new PurchaseOrder(); o.user=u; o.address=address; o.method=method; double sub=0;
    for(var e:cart.entrySet()){
      Variant v=variants.lockById(e.getKey()).orElseThrow();
      if(v.stock<e.getValue()) throw new IllegalStateException("Not enough stock for "+v.product.name+" ("+v.size+")");
      OrderItem it=new OrderItem(); it.order=o; it.variant=v; it.qty=e.getValue(); it.unitPrice=v.product.finalPrice();
      o.items.add(it); sub+=it.unitPrice*it.qty; }
    o.total=sub+delivery(sub); o=orders.save(o); o.paymentRef="order_"+o.id;
    if("COD".equals(method)){ if(!deduct(o)) throw new IllegalStateException("Out of stock"); o.status="PLACED"; }
    return o; }

  private boolean deduct(PurchaseOrder o){
    List<Variant> vs=new ArrayList<>();
    for(OrderItem i:o.items){ Variant v=variants.lockById(i.variant.id).orElseThrow(); if(v.stock<i.qty) return false; vs.add(v); }
    for(int k=0;k<vs.size();k++) vs.get(k).stock-=o.items.get(k).qty;
    return true; }

  public String sign(String ref,String pid){
    try{ Mac m=Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec((live()?keySecret:secret).getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
      return HexFormat.of().formatHex(m.doFinal((ref+"|"+pid).getBytes(StandardCharsets.UTF_8))); }
    catch(Exception e){ throw new IllegalStateException(e); } }

  /** Order becomes PAID and stock is reduced ONLY if the signature is valid. */
  @Transactional
  public boolean verify(String ref,String pid,String sig){
    if(!MessageDigest.isEqual(sign(ref,pid).getBytes(StandardCharsets.UTF_8),sig.getBytes(StandardCharsets.UTF_8))) return false;
    PurchaseOrder o=orders.findByPaymentRef(ref); if(o==null||!"PENDING".equals(o.status)) return false;
    if(!deduct(o)){ o.status="CANCELLED"; return false; }
    o.status="PAID"; return true; }

  /** Content-based recommender: TF-IDF vectors + cosine similarity. */
  public List<Product> similar(Product p,int n){
    List<Product> all=products.findAll(); Map<String,Integer> df=new HashMap<>(); List<Map<String,Double>> tf=new ArrayList<>();
    for(Product x:all){ Map<String,Double> m=new HashMap<>();
      for(String t:(x.name+" "+x.category+" "+x.sportType+" "+x.description).toLowerCase().split("\\W+")) if(t.length()>2) m.merge(t,1.0,Double::sum);
      m.keySet().forEach(t->df.merge(t,1,Integer::sum)); tf.add(m); }
    int N=all.size(), k=0; List<Map<String,Double>> vec=new ArrayList<>();
    for(int i=0;i<N;i++){ Map<String,Double> v=new HashMap<>(); tf.get(i).forEach((t,c)->v.put(t,c*(Math.log((1.0+N)/(1.0+df.get(t)))+1))); vec.add(v); if(all.get(i).id.equals(p.id)) k=i; }
    final int me=k; double[] sc=new double[N]; for(int i=0;i<N;i++) sc[i]=cos(vec.get(me),vec.get(i));
    return IntStream.range(0,N).boxed().filter(i->i!=me).sorted((a,b)->Double.compare(sc[b],sc[a])).limit(n).map(all::get).collect(Collectors.toList()); }
  static double cos(Map<String,Double> a,Map<String,Double> b){
    double d=0,na=0,nb=0; for(var e:a.entrySet()){ na+=e.getValue()*e.getValue(); Double y=b.get(e.getKey()); if(y!=null) d+=e.getValue()*y; }
    for(double y:b.values()) nb+=y*y; return d==0?0:d/Math.sqrt(na*nb); }

  /** "Frequently bought together": counts products that share an order with p. */
  public List<Product> together(Product p,int n){
    Map<Long,Integer> c=new HashMap<>(); Map<Long,Product> byId=new HashMap<>();
    for(PurchaseOrder o:orders.findAll()){
      if("PENDING".equals(o.status)||"CANCELLED".equals(o.status)) continue;
      Map<Long,Product> ps=new HashMap<>(); for(OrderItem i:o.items) ps.put(i.variant.product.id,i.variant.product);
      if(ps.containsKey(p.id)) ps.forEach((id,x)->{ if(!id.equals(p.id)){ c.merge(id,1,Integer::sum); byId.put(id,x); } }); }
    return c.entrySet().stream().sorted((a,b)->b.getValue()-a.getValue()).limit(n).map(e->byId.get(e.getKey())).collect(Collectors.toList()); }
}
