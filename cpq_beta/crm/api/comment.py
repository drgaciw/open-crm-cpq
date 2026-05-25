from collections.abc import Iterable

import frappe
from frappe import _
from bs4 import BeautifulSoup


def on_update(self, method):
    notify_mentions(self)


def notify_mentions(doc):
    """
    Extract mentions from `content`, and notify.
    `content` must have `HTML` content.
    """
    content = getattr(doc, "content", None)
    if not content:
        return
    mentions = extract_mentions(content)
    if not mentions:
        return

    # Hoist loop-invariant operations outside the loop
    owner = frappe.get_cached_value("User", doc.owner, "full_name")
    doctype = doc.reference_doctype
    if doctype.startswith("CRM "):
        doctype = doctype[4:].lower()
    notification_text = f"""
        <div class="mb-2 leading-5 text-gray-600">
            <span class="font-medium text-gray-900">{ owner }</span>
            <span>{ _('mentioned you in {0}').format(doctype) }</span>
            <span class="font-medium text-gray-900">{ doc.reference_name }</span>
        </div>
    """

    # Batch-fetch existing notifications in a single query — O(1) set lookup replaces N frappe.db.exists() calls
    emails = [mention.email for mention in mentions]
    existing_notifications = set(
        frappe.get_all(
            "CRM Notification",
            filters={
                "from_user": doc.owner,
                "to_user": ["in", emails],
                "type": "Mention",
                "notification_type_doctype": "Comment",
                "notification_type_doc": doc.name,
                "reference_doctype": doc.reference_doctype,
                "reference_name": doc.reference_name,
            },
            pluck="to_user",
        )
    )

    notifications_to_insert = []

    for mention in mentions:
        if mention.email in existing_notifications:
            continue

        values = dict(
            doctype="CRM Notification",
            from_user=doc.owner,
            to_user=mention.email,
            type="Mention",
            message=doc.content,
            notification_text=notification_text,
            notification_type_doctype="Comment",
            notification_type_doc=doc.name,
            reference_doctype=doc.reference_doctype,
            reference_name=doc.reference_name,
        )
        notifications_to_insert.append(values)

        # Track newly inserted notifications to prevent in-flight duplicates
        existing_notifications.add(mention.email)

    if notifications_to_insert:
        frappe.deferred_insert("CRM Notification", notifications_to_insert)


def extract_mentions(html):
    if not html:
        return []
    soup = BeautifulSoup(html, "html.parser")
    mentions = []
    for d in soup.find_all("span", attrs={"data-type": "mention"}):
        mentions.append(
            frappe._dict(full_name=d.get("data-label"), email=d.get("data-id"))
        )
    return mentions


@frappe.whitelist()
def add_attachments(name: str, attachments: Iterable[str | dict]) -> None:
    """Add attachments to the given Comment

    :param name: Comment name
    :param attachments: File names or dicts with keys "fname" and "fcontent"
    """
    comment = frappe.get_doc("Comment", name)
    if comment.reference_doctype and comment.reference_name:
        if not frappe.has_permission(
            comment.reference_doctype, "write", comment.reference_name
        ):
            frappe.throw(_("Not permitted"), frappe.PermissionError)

    for a in attachments:
        if isinstance(a, str):
            attach = frappe.db.get_value(
                "File", {"name": a}, ["file_url", "is_private"], as_dict=1
            )
            file_args = {
                "file_url": attach.file_url,
                "is_private": attach.is_private,
            }
        elif isinstance(a, dict) and "fcontent" in a and "fname" in a:
            file_args = {
                "file_name": a["fname"],
                "content": a["fcontent"],
                "is_private": 1,
            }
        else:
            continue

        file_args.update(
            {
                "attached_to_doctype": "Comment",
                "attached_to_name": name,
                "folder": "Home/Attachments",
            }
        )

        _file = frappe.new_doc("File")
        _file.update(file_args)
        _file.save()
