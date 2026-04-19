# Copyright (c) 2024, Frappe Technologies Pvt. Ltd. and contributors
# For license information, please see license.txt

import json
import frappe
from frappe import _
from frappe.model.document import Document

ALLOWED_LAYOUT_DOCTYPES = {"CRM Lead", "CRM Deal", "CRM Organization", "Contact"}


class CRMFieldsLayout(Document):
	pass

@frappe.whitelist()
def get_fields_layout(doctype: str, type: str):
	if doctype not in ALLOWED_LAYOUT_DOCTYPES:
		frappe.throw(_("Invalid doctype"), frappe.ValidationError)

	sections = []
	# Bolt Optimization: Used frappe.db.get_value to prevent full document loading
	layout_json = frappe.db.get_value("CRM Fields Layout", {"dt": doctype, "type": type}, "layout")
	if not layout_json:
		return []

	if layout_json:
		sections = json.loads(layout_json)

	allowed_fields = []
	for section in sections:
		if not section.get("fields"):
			continue
		allowed_fields.extend(section.get("fields"))

	fields = frappe.get_meta(doctype).fields
	fields = [field for field in fields if field.fieldname in allowed_fields]

	for section in sections:
		for field in section.get("fields") if section.get("fields") else []:
			field = next((f for f in fields if f.fieldname == field), None)
			if field:
				if field.fieldtype == "Select":
					field.options = field.options.split("\n")
					field.options = [{"label": _(option), "value": option} for option in field.options]
					field.options.insert(0, {"label": "", "value": ""})
				field = {
					"label": _(field.label),
					"name": field.fieldname,
					"type": field.fieldtype,
					"options": field.options,
					"mandatory": field.reqd,
				}
				section["fields"][section.get("fields").index(field["name"])] = field

	return sections or []


@frappe.whitelist()
def save_fields_layout(doctype: str, type: str, layout: str):
	if doctype not in ALLOWED_LAYOUT_DOCTYPES:
		frappe.throw(_("Invalid doctype"), frappe.ValidationError)

	if "Sales Manager" not in frappe.get_roles() and frappe.session.user != "Administrator":
		frappe.throw(_("Not permitted"), frappe.PermissionError)

	if frappe.db.exists("CRM Fields Layout", {"dt": doctype, "type": type}):
		doc = frappe.get_doc("CRM Fields Layout", {"dt": doctype, "type": type})
	else:
		doc = frappe.new_doc("CRM Fields Layout")

	doc.update({
		"dt": doctype,
		"type": type,
		"layout": layout,
	})
	doc.save()

	return doc.layout
