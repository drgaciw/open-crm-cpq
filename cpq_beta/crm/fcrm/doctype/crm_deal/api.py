import frappe
from frappe import _

from crm.api.doc import get_fields_meta, get_assigned_users
from crm.fcrm.doctype.crm_form_script.crm_form_script import get_form_script

@frappe.whitelist()
def get_deal(name):
	if not frappe.has_permission("CRM Deal", "read", name):
		frappe.throw(_("Not permitted"), frappe.PermissionError)

	Deal = frappe.qb.DocType("CRM Deal")

	query = (
		frappe.qb.from_(Deal)
		.select(
			Deal.name,
			Deal.organization,
			Deal.annual_revenue,
			Deal.status,
			Deal.email,
			Deal.mobile_no,
			Deal.phone,
			Deal.deal_owner,
			Deal.lead,
			Deal.currency,
			Deal.sla,
			Deal.sla_status,
			Deal.response_by,
			Deal.first_response_time,
			Deal.first_responded_on,
			Deal.owner,
			Deal.creation,
			Deal.modified,
			Deal.communication_status
		)
		.where(Deal.name == name)
		.limit(1)
	)

	deal = query.run(as_dict=True)
	if not len(deal):
		frappe.throw(_("Deal not found"), frappe.DoesNotExistError)
	deal = deal.pop()


	deal["contacts"] = frappe.get_all(
		"CRM Contacts",
		filters={"parenttype": "CRM Deal", "parent": deal.name},
		fields=["contact", "is_primary"],
	)

	deal["doctype"] = "CRM Deal"
	deal["fields_meta"] = get_fields_meta("CRM Deal") 
	deal["_form_script"] = get_form_script('CRM Deal')
	deal["_assign"] = get_assigned_users("CRM Deal", deal.name, deal.owner)
	return deal

@frappe.whitelist()
def get_deal_contacts(name):
	if not frappe.has_permission("CRM Deal", "read", name):
		frappe.throw(_("Not permitted"), frappe.PermissionError)

	contacts = frappe.get_all(
		"CRM Contacts",
		filters={"parenttype": "CRM Deal", "parent": name},
		fields=["contact", "is_primary"],
	)

	if not contacts:
		return []

	contact_names = [c.contact for c in contacts if frappe.db.exists("Contact", c.contact)]
	if not contact_names:
		return []

	contact_is_primary = {c.contact: c.is_primary for c in contacts}

	contact_data = frappe.get_all(
		"Contact",
		filters={"name": ["in", contact_names]},
		fields=["name", "image", "full_name"],
	)

	all_emails = frappe.get_all(
		"Contact Email",
		filters={"parent": ["in", contact_names]},
		fields=["parent", "email_id", "is_primary"],
		order_by="is_primary desc",
	)

	all_phones = frappe.get_all(
		"Contact Phone",
		filters={"parent": ["in", contact_names]},
		fields=["parent", "phone", "is_primary"],
		order_by="is_primary desc",
	)

	emails_by_contact = {}
	for email in all_emails:
		if email.parent not in emails_by_contact:
			emails_by_contact[email.parent] = email.email_id

	phones_by_contact = {}
	for phone in all_phones:
		if phone.parent not in phones_by_contact:
			phones_by_contact[phone.parent] = phone.phone

	deal_contacts = []
	for contact in contact_data:
		_contact = {
			"name": contact.name,
			"image": contact.image,
			"full_name": contact.full_name,
			"email": emails_by_contact.get(contact.name, ""),
			"mobile_no": phones_by_contact.get(contact.name, ""),
			"is_primary": contact_is_primary.get(contact.name, 0),
		}
		deal_contacts.append(_contact)

	return deal_contacts