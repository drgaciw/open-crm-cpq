# Copyright (c) 2023, Frappe Technologies Pvt. Ltd. and contributors
# For license information, please see license.txt

import frappe
from frappe import _
from frappe.model.document import Document


class CRMCallLog(Document):
		@staticmethod
		def default_list_data():
			columns = [
				{
					'label': 'From',
					'type': 'Link',
					'key': 'caller',
					'options': 'User',
					'width': '9rem',
				},
				{
					'label': 'To',
					'type': 'Link',
					'key': 'receiver',
					'options': 'User',
					'width': '9rem',
				},
				{
					'label': 'Type',
					'type': 'Select',
					'key': 'type',
					'width': '9rem',
				},
				{
					'label': 'Status',
					'type': 'Select',
					'key': 'status',
					'width': '9rem',
				},
				{
					'label': 'Duration',
					'type': 'Duration',
					'key': 'duration',
					'width': '6rem',
				},
				{
					'label': 'From (number)',
					'type': 'Data',
					'key': 'from',
					'width': '9rem',
				},
				{
					'label': 'To (number)',
					'type': 'Data',
					'key': 'to',
					'width': '9rem',
				},
				{
					'label': 'Created On',
					'type': 'Datetime',
					'key': 'creation',
					'width': '8rem',
				},
				]
			rows = [
				"name",
				"caller",
				"receiver",
				"type",
				"status",
				"duration",
				"from",
				"to",
				"note",
				"recording_url",
				"reference_doctype",
				"reference_docname",
				"creation",
			]
			return {'columns': columns, 'rows': rows}

@frappe.whitelist()
def create_lead_from_call_log(call_log):
	"""
	Creates a new CRM Lead from a Call Log entry.
	Actively used by the frontend CallLogModal.vue component.
	"""
	if not frappe.has_permission("CRM Lead", "create"):
		frappe.throw(_("Not permitted"), frappe.PermissionError)

	if isinstance(call_log, str):
		import json
		call_log = json.loads(call_log)

	if not isinstance(call_log, dict):
		frappe.throw(_("Invalid call_log data"), frappe.ValidationError)

	from_number = call_log.get("from")
	if not from_number:
		frappe.throw(_("Missing 'from' field in call_log"), frappe.ValidationError)

	lead = frappe.new_doc("CRM Lead")
	lead.first_name = "Lead from call " + from_number
	lead.mobile_no = from_number
	lead.lead_owner = frappe.session.user
	lead.save()

	frappe.db.set_value("CRM Call Log", call_log.get("name"), {
		"reference_doctype": "CRM Lead",
		"reference_docname": lead.name
	})

	if call_log.get("note"):
		frappe.db.set_value("FCRM Note", call_log.get("note"), {
			"reference_doctype": "CRM Lead",
			"reference_docname": lead.name
		})

	return lead.name