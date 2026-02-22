import frappe
from frappe.query_builder import Order


@frappe.whitelist()
def get_notifications():
    Notification = frappe.qb.DocType("CRM Notification")
    query = (
        frappe.qb.from_(Notification)
        .select(
            Notification.name,
            Notification.creation,
            Notification.from_user,
            Notification.to_user,
            Notification.type,
            Notification.read,
            Notification.comment,
            Notification.notification_text,
            Notification.notification_type_doctype,
            Notification.notification_type_doc,
            Notification.reference_doctype,
            Notification.reference_name
        )
        .where(Notification.to_user == frappe.session.user)
        .orderby("creation", order=Order.desc)
    )
    notifications = query.run(as_dict=True)

    from_users = list(set([n.from_user for n in notifications if n.from_user]))
    user_names = {}
    if from_users:
        User = frappe.qb.DocType("User")
        user_data = (
            frappe.qb.from_(User)
            .select(User.name, User.full_name)
            .where(User.name.isin(from_users))
            .run(as_dict=True)
        )
        user_names = {u.name: u.full_name for u in user_data}

    _notifications = []
    for notification in notifications:
        _notifications.append(
            {
                "creation": notification.creation,
                "from_user": {
                    "name": notification.from_user,
                    "full_name": user_names.get(notification.from_user, ""),
                },
                "type": notification.type,
                "to_user": notification.to_user,
                "read": notification.read,
                "comment": notification.comment,
                "notification_text": notification.notification_text,
                "notification_type_doctype": notification.notification_type_doctype,
                "notification_type_doc": notification.notification_type_doc,
                "reference_doctype": (
                    "deal" if notification.reference_doctype == "CRM Deal" else "lead"
                ),
                "reference_name": notification.reference_name,
                "route_name": (
                    "Deal" if notification.reference_doctype == "CRM Deal" else "Lead"
                ),
            }
        )

    return _notifications


@frappe.whitelist()
def mark_as_read(doc=None):
    user = frappe.session.user
    Notification = frappe.qb.DocType("CRM Notification")

    query = (
        frappe.qb.update(Notification)
        .set(Notification.read, True)
        .where(Notification.to_user == user)
        .where(Notification.read == False)
    )

    if doc:
        query = query.where(
            (Notification.comment == doc) | (Notification.notification_type_doc == doc)
        )

    query.run()
