package com.opencrm.order.domain;
import com.opencrm.common.order.AcceptedOrderSnapshot;
import java.util.*;
import java.math.BigDecimal;
public class SnapshotValidator {
 public List<String> validate(AcceptedOrderSnapshot s) {
  if(s==null || s.schemaVersion()!=1 || !"USD".equals(s.currency()) || s.acceptedRevision()<1 || s.acceptedAt()==null) throw OrderProblem.invalid("Unsupported accepted snapshot");
  required(s.snapshotId()); required(s.scopeId()); required(s.customerId()); required(s.quoteNumber());
  try { if(!UUID.fromString(s.quoteId()).toString().equals(s.quoteId())) throw new IllegalArgumentException(); } catch(Exception e) { throw OrderProblem.invalid("Canonical quote UUID required"); }
  if(s.lines()==null || s.lines().isEmpty()) throw OrderProblem.invalid("Order requires lines");
  Set<String> ids=new HashSet<>(); var holds=new ArrayList<String>();
  BigDecimal subtotal=BigDecimal.ZERO,discount=BigDecimal.ZERO,tax=BigDecimal.ZERO,total=BigDecimal.ZERO;
  for(var l:s.lines()) {
   required(l.sourceLineId()); required(l.productId());required(l.description());required(l.uom());
   if(!ids.add(l.sourceLineId()) || l.quantity()<1) throw OrderProblem.invalid("Invalid line identity or quantity");
   if(!Set.of("PHYSICAL","DIGITAL","SERVICE").contains(l.fulfillmentType()==null?"":l.fulfillmentType())) holds.add("MAPPING:"+l.sourceLineId());
   BigDecimal gross=amount(l.unitPrice()).multiply(BigDecimal.valueOf(l.quantity()));
   BigDecimal d=amount(l.discount()), t=amount(l.tax()), net=amount(l.total());
   if(d.compareTo(gross)>0 || gross.subtract(d).add(t).compareTo(net)!=0) throw OrderProblem.invalid("Line economics do not reconcile");
   subtotal=subtotal.add(gross);discount=discount.add(d);tax=tax.add(t);total=total.add(net);
   if("ONE_TIME".equals(l.chargeType())) { if(!"ONE_TIME".equals(l.billingFrequency())) throw OrderProblem.invalid("One-time billing mismatch"); }
   else if("RECURRING".equals(l.chargeType())) {
    if(!Set.of("MONTHLY","ANNUAL").contains(l.billingFrequency()==null?"":l.billingFrequency())) throw OrderProblem.invalid("Unsupported recurrence");
    if(l.startDate()==null || l.endDate()==null) holds.add("SOURCE_DATES:"+l.sourceLineId());
    else if(!l.endDate().isAfter(l.startDate())) throw OrderProblem.invalid("Invalid source dates");
   } else throw OrderProblem.invalid("Unsupported charge type");
  }
  if(subtotal.compareTo(amount(s.subtotal()))!=0 || discount.compareTo(amount(s.discount()))!=0 || tax.compareTo(amount(s.tax()))!=0 || total.compareTo(amount(s.total()))!=0) throw OrderProblem.invalid("Header economics do not reconcile");
  if(blank(s.address())) holds.add("ADDRESS"); if(blank(s.approvalReference())) holds.add("APPROVAL"); if(blank(s.taxEvidence())) holds.add("TAX"); if(blank(s.configurationReference())) holds.add("CONFIGURATION");
  return List.copyOf(holds);
 }
 private static boolean blank(String s) { return s==null || s.isBlank(); }
 private static void required(String s) { if(blank(s) || s.length()>500) throw OrderProblem.invalid("Missing or oversized source identity"); }
 private static BigDecimal amount(String s) {
  if(s==null || !s.matches("[0-9]{1,14}(\\.[0-9]{1,2})?")) throw OrderProblem.invalid("Invalid decimal amount");
  return new BigDecimal(s);
 }
}
