import frappe
from frappe import _

from crm.api.doc import get_fields_meta, get_assigned_users
from crm.fcrm.doctype.crm_form_script.crm_form_script import get_form_script

@frappe.whitelist()
def get_lead(name):
	if not frappe.has_permission("CRM Lead", "read", name):
		frappe.throw(_("Not permitted"), frappe.PermissionError)

	Lead = frappe.qb.DocType("CRM Lead")

	query = (
		frappe.qb.from_(Lead)
		.select(
			Lead.name,
			Lead.salutation,
			Lead.first_name,
			Lead.last_name,
			Lead.email,
			Lead.mobile_no,
			Lead.gender,
			Lead.organization,
			Lead.website,
			Lead.territory,
			Lead.industry,
			Lead.annual_revenue,
			Lead.status,
			Lead.lead_owner,
			Lead.sla,
			Lead.sla_status,
			Lead.response_by,
			Lead.first_response_time,
			Lead.first_responded_on,
			Lead.owner,
			Lead.creation,
			Lead.modified,
			Lead.converted,
			Lead.communication_status
		)
		.where(Lead.name == name)
		.limit(1)
	)

	lead = query.run(as_dict=True)
	if not len(lead):
		frappe.throw(_("Lead not found"), frappe.DoesNotExistError)
	lead = lead.pop()

	lead["doctype"] = "CRM Lead"
	lead["fields_meta"] = get_fields_meta("CRM Lead")
	lead["_form_script"] = get_form_script('CRM Lead')
	lead["_assign"] = get_assigned_users("CRM Lead", lead.name, lead.owner)
	return lead
