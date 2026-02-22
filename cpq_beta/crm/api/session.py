import frappe


@frappe.whitelist()
def get_users():
	users = frappe.qb.get_query(
		"User",
		fields=["name", "email", "enabled", "user_image", "first_name", "last_name", "full_name", "user_type"],
		order_by="full_name asc",
		distinct=True,
	).run(as_dict=1)

	for user in users:
		if frappe.session.user == user.name:
			user.session_user = True

		user.is_manager = (
			"Sales Manager" in frappe.get_roles(user.name) or user.name == "Administrator"
		)
	return users

@frappe.whitelist()
def get_contacts(page_length=100, start=0):
	contacts = frappe.get_all(
		"Contact",
		fields=[
			"name",
			"salutation",
			"first_name",
			"last_name",
			"full_name",
			"gender",
			"address",
			"designation",
			"image",
			"email_id",
			"mobile_no",
			"phone",
			"company_name",
			"modified"
		],
		order_by="first_name asc",
		distinct=True,
		limit_page_length=page_length,
		limit_start=start,
	)

	if not contacts:
		return []

	contact_names = [c.name for c in contacts]

	all_emails = frappe.get_all(
		"Contact Email",
		filters={"parenttype": "Contact", "parent": ["in", contact_names]},
		fields=["parent", "name", "email_id", "is_primary"],
	)

	all_phones = frappe.get_all(
		"Contact Phone",
		filters={"parenttype": "Contact", "parent": ["in", contact_names]},
		fields=["parent", "name", "phone", "is_primary_phone", "is_primary_mobile_no"],
	)

	emails_by_contact = {}
	for email in all_emails:
		if email.parent not in emails_by_contact:
			emails_by_contact[email.parent] = []
		emails_by_contact[email.parent].append({
			"name": email.name,
			"email_id": email.email_id,
			"is_primary": email.is_primary,
		})

	phones_by_contact = {}
	for phone in all_phones:
		if phone.parent not in phones_by_contact:
			phones_by_contact[phone.parent] = []
		phones_by_contact[phone.parent].append({
			"name": phone.name,
			"phone": phone.phone,
			"is_primary_phone": phone.is_primary_phone,
			"is_primary_mobile_no": phone.is_primary_mobile_no,
		})

	for contact in contacts:
		contact["email_ids"] = emails_by_contact.get(contact.name, [])
		contact["phone_nos"] = phones_by_contact.get(contact.name, [])

	return contacts

@frappe.whitelist()
def get_lead_contacts():
	lead_contacts = frappe.get_all(
		"CRM Lead",
		fields=[
			"name",
			"lead_name",
			"mobile_no",
			"phone",
			"image",
			"modified"
		],
		filters={"converted": 0},
		order_by="lead_name asc",
		distinct=True,
	)

	return lead_contacts

@frappe.whitelist()
def get_organizations(page_length=100, start=0):
	Organization = frappe.qb.DocType("CRM Organization")
	organizations = (
		frappe.qb.from_(Organization)
		.select(
			Organization.name,
			Organization.organization_name,
			Organization.website,
			Organization.territory,
			Organization.annual_revenue,
			Organization.industry,
			Organization.image,
			Organization.modified
		)
		.orderby(Organization.name)
		.limit(page_length)
		.offset(start)
		.run(as_dict=True)
	)

	return organizations
