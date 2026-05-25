import frappe
import json
from frappe import _
from crm.api.doc import get_assigned_users


def validate(doc, method):
    if doc.type == "Incoming" and doc.get("from"):
        name, doctype = get_lead_or_deal_from_number(doc.get("from"))
        doc.reference_doctype = doctype
        doc.reference_name = name


def on_update(doc, method):
    frappe.publish_realtime(
        "whatsapp_message",
        {
            "reference_doctype": doc.reference_doctype,
            "reference_name": doc.reference_name,
        },
    )

    notify_agent(doc)


def notify_agent(doc):
    if doc.type == "Incoming":
        doctype = doc.reference_doctype
        if doctype.startswith("CRM "):
            doctype = doctype[4:].lower()
        notification_text = f"""
			<div class="mb-2 leading-5 text-gray-600">
				<span class="font-medium text-gray-900">{ _('You') }</span>
				<span>{ _('received a whatsapp message in {0}').format(doctype) }</span>
				<span class="font-medium text-gray-900">{ doc.reference_name }</span>
			</div>
		"""
        assigned_users = get_assigned_users(doc.reference_doctype, doc.reference_name)
        if not assigned_users:
            return

        # Batch-fetch existing notifications in a single query — O(1) set lookup replaces N frappe.db.exists() calls
        existing_notifications = set(
            frappe.get_all(
                "CRM Notification",
                filters={
                    "notification_type_doctype": "WhatsApp Message",
                    "notification_type_doc": doc.name,
                    "reference_doctype": doc.reference_doctype,
                    "reference_name": doc.reference_name,
                    "type": "WhatsApp",
                },
                pluck="to_user",
            )
        )

        new_users = [u for u in assigned_users if u not in existing_notifications]
        if not new_users:
            return

        now = frappe.utils.now()
        fields = [
            "name",
            "creation",
            "modified",
            "modified_by",
            "owner",
            "docstatus",
            "from_user",
            "to_user",
            "type",
            "message",
            "notification_text",
            "notification_type_doctype",
            "notification_type_doc",
            "reference_doctype",
            "reference_name",
        ]

        values = []
        for user in new_users:
            values.append(
                (
                    frappe.generate_hash(length=10),
                    now,
                    now,
                    frappe.session.user if getattr(frappe, "session", None) else "Administrator",
                    frappe.session.user if getattr(frappe, "session", None) else "Administrator",
                    0,
                    doc.owner,
                    user,
                    "WhatsApp",
                    doc.message,
                    notification_text,
                    "WhatsApp Message",
                    doc.name,
                    doc.reference_doctype,
                    doc.reference_name,
                )
            )

        frappe.db.bulk_insert("CRM Notification", fields=fields, values=values)
        frappe.publish_realtime("crm_notification")


def get_lead_or_deal_from_number(number):
    """Get lead/deal from the given number."""

    def find_record(doctype, mobile_no, only_unconverted=False):
        if doctype not in {"CRM Deal", "CRM Lead"}:
            return None
        if not mobile_no:
            return None

        mobile_no = parse_mobile_no(mobile_no)
        if not mobile_no:
            return None

        if doctype == "CRM Deal":
            query = """
			SELECT name
			FROM `tabCRM Deal`
			WHERE CONCAT('+', REGEXP_REPLACE(mobile_no, '[^0-9]', '')) = %(mobile_no)s
		"""
        elif doctype == "CRM Lead":
            if only_unconverted:
                query = """
			SELECT name
			FROM `tabCRM Lead`
			WHERE CONCAT('+', REGEXP_REPLACE(mobile_no, '[^0-9]', '')) = %(mobile_no)s
AND converted is not True
		"""
            else:
                query = """
			SELECT name
			FROM `tabCRM Lead`
			WHERE CONCAT('+', REGEXP_REPLACE(mobile_no, '[^0-9]', '')) = %(mobile_no)s
		"""

        data = frappe.db.sql(query, {"mobile_no": mobile_no}, as_dict=True)
        return data[0].name if data else None

    doctype = "CRM Deal"

    doc = find_record(doctype, number) or None
    if not doc:
        doctype = "CRM Lead"
        doc = find_record(doctype, number, only_unconverted=True)
        if not doc:
            doc = find_record(doctype, number)

    return doc, doctype


def parse_mobile_no(mobile_no: str):
    """Parse mobile number to remove spaces, brackets, etc.
    >>> parse_mobile_no('+91 (766) 667 6666')
    ... '+917666676666'
    """
    return "".join([c for c in mobile_no if c.isdigit() or c == "+"])


@frappe.whitelist()
def is_whatsapp_enabled():
    if not frappe.db.exists("DocType", "WhatsApp Settings"):
        return False
    return frappe.get_cached_value("WhatsApp Settings", "WhatsApp Settings", "enabled")


@frappe.whitelist()
def is_whatsapp_installed():
    if not frappe.db.exists("DocType", "WhatsApp Settings"):
        return False
    return True


@frappe.whitelist()
def get_whatsapp_messages(reference_doctype, reference_name):
    if reference_doctype not in ("CRM Deal", "CRM Lead"):
        frappe.throw(_("Invalid doctype"), frappe.ValidationError)
    if not frappe.has_permission(reference_doctype, "read", reference_name):
        frappe.throw(_("Not permitted"), frappe.PermissionError)

    if not frappe.db.exists("DocType", "WhatsApp Message"):
        return []
    messages = frappe.get_all(
        "WhatsApp Message",
        filters={
            "reference_doctype": reference_doctype,
            "reference_name": reference_name,
        },
        fields=[
            "name",
            "type",
            "to",
            "from",
            "content_type",
            "message_type",
            "attach",
            "template",
            "use_template",
            "message_id",
            "is_reply",
            "reply_to_message_id",
            "creation",
            "message",
            "status",
            "reference_doctype",
            "reference_name",
            "template_parameters",
            "template_header_parameters",
        ],
    )

    # Filter messages to get only Template messages
    template_messages = [
        message for message in messages if message["message_type"] == "Template"
    ]

    # Pre-fetch all templates used by these messages in a single query
    template_names = list(
        set(msg["template"] for msg in template_messages if msg.get("template"))
    )

    templates_dict = {}
    if template_names:
        templates_data = frappe.get_all(
            "WhatsApp Templates",
            filters={"name": ["in", template_names]},
            fields=["name", "template_name", "template", "header", "footer"],
        )
        for t in templates_data:
            # We convert to frappe._dict to access properties with dot notation like the original get_doc behavior
            t_dict = frappe._dict(t)
            templates_dict[t_dict.name] = t_dict

    # Iterate through template messages
    for template_message in template_messages:
        # Find the template that this message is using
        template = templates_dict.get(template_message["template"])

        # If the template is found, add the template details to the template message
        if template:
            template_message["template_name"] = template.template_name

            # Since we're reusing the dict, create a copy of the template properties
            # to parse parameters specific to this message
            parsed_template = template.template
            if template_message["template_parameters"] and parsed_template:
                parameters = json.loads(template_message["template_parameters"])
                parsed_template = parse_template_parameters(parsed_template, parameters)

            template_message["template"] = parsed_template

            parsed_header = template.header
            if template_message["template_header_parameters"] and parsed_header:
                header_parameters = json.loads(
                    template_message["template_header_parameters"]
                )
                parsed_header = parse_template_parameters(
                    parsed_header, header_parameters
                )

            template_message["header"] = parsed_header
            template_message["footer"] = template.footer

    # Filter messages to get only reaction messages
    reaction_messages = [
        message for message in messages if message["content_type"] == "reaction"
    ]

    # Iterate through reaction messages
    for reaction_message in reaction_messages:
        # Find the message that this reaction is reacting to
        reacted_message = next(
            (
                m
                for m in messages
                if m["message_id"] == reaction_message["reply_to_message_id"]
            ),
            None,
        )

        # If the reacted message is found, add the reaction to it
        if reacted_message:
            reacted_message["reaction"] = reaction_message["message"]

    cached_from_name = get_from_name(
        {"reference_doctype": reference_doctype, "reference_name": reference_name}
    )

    for message in messages:
        from_name = cached_from_name if message["from"] else _("You")
        message["from_name"] = from_name
    # Filter messages to get only replies
    reply_messages = [message for message in messages if message["is_reply"]]

    # Iterate through reply messages
    for reply_message in reply_messages:
        # Find the message that this message is replying to
        replied_message = next(
            (
                m
                for m in messages
                if m["message_id"] == reply_message["reply_to_message_id"]
            ),
            None,
        )

        # If the replied message is found, add the reply details to the reply message
        from_name = (
            cached_from_name
            if replied_message and replied_message["from"]
            else _("You")
        )
        if replied_message:
            message = replied_message["message"]
            if replied_message["message_type"] == "Template":
                message = replied_message["template"]
            reply_message["reply_message"] = message
            reply_message["header"] = replied_message.get("header") or ""
            reply_message["footer"] = replied_message.get("footer") or ""
            reply_message["reply_to"] = replied_message["name"]
            reply_message["reply_to_type"] = replied_message["type"]
            reply_message["reply_to_from"] = from_name

    return [message for message in messages if message["content_type"] != "reaction"]


@frappe.whitelist()
def create_whatsapp_message(
    reference_doctype,
    reference_name,
    message,
    to,
    attach,
    reply_to,
    content_type="text",
):
    if not frappe.has_permission(reference_doctype, "read", reference_name):
        frappe.throw(_("Not permitted"), frappe.PermissionError)

    doc = frappe.new_doc("WhatsApp Message")

    if reply_to:
        reply_doc = frappe.get_doc("WhatsApp Message", reply_to)
        doc.update(
            {
                "is_reply": True,
                "reply_to_message_id": reply_doc.message_id,
            }
        )

    doc.update(
        {
            "reference_doctype": reference_doctype,
            "reference_name": reference_name,
            "message": message or attach,
            "to": to,
            "attach": attach,
            "content_type": content_type,
        }
    )
    doc.insert(ignore_permissions=True)
    return doc.name


@frappe.whitelist()
def send_whatsapp_template(reference_doctype, reference_name, template, to):
    if not frappe.has_permission(reference_doctype, "read", reference_name):
        frappe.throw(_("Not permitted"), frappe.PermissionError)

    doc = frappe.new_doc("WhatsApp Message")
    doc.update(
        {
            "reference_doctype": reference_doctype,
            "reference_name": reference_name,
            "message_type": "Template",
            "message": "Template message",
            "content_type": "text",
            "use_template": True,
            "template": template,
            "to": to,
        }
    )
    doc.insert(ignore_permissions=True)
    return doc.name


@frappe.whitelist()
def react_on_whatsapp_message(emoji, reply_to_name):
    reply_to_doc = frappe.get_doc("WhatsApp Message", reply_to_name)

    if not frappe.has_permission(
        reply_to_doc.reference_doctype, "read", reply_to_doc.reference_name
    ):
        frappe.throw(_("Not permitted"), frappe.PermissionError)

    to = reply_to_doc.type == "Incoming" and reply_to_doc.get("from") or reply_to_doc.to
    doc = frappe.new_doc("WhatsApp Message")
    doc.update(
        {
            "reference_doctype": reply_to_doc.reference_doctype,
            "reference_name": reply_to_doc.reference_name,
            "message": emoji,
            "to": to,
            "reply_to_message_id": reply_to_doc.message_id,
            "content_type": "reaction",
        }
    )
    doc.insert(ignore_permissions=True)
    return doc.name


def parse_template_parameters(string, parameters):
    for i, parameter in enumerate(parameters, start=1):
        placeholder = "{{" + str(i) + "}}"
        string = string.replace(placeholder, parameter)

    return string


def get_from_name(message):
    doc = frappe.get_doc(message["reference_doctype"], message["reference_name"])
    from_name = ""
    if message["reference_doctype"] == "CRM Deal":
        if doc.get("contacts"):
            for c in doc.get("contacts"):
                if c.is_primary:
                    from_name = c.full_name or c.mobile_no
                    break
        else:
            from_name = doc.get("lead_name")
    else:
        from_name = (
            " ".join(filter(None, [doc.get("first_name"), doc.get("last_name")])) or ""
        )
    return from_name
