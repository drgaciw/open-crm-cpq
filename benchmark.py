import frappe
from timeit import default_timer as timer

def run_bench():
    frappe.init(site="127.0.0.1") # assuming local frappe setup if available
    frappe.connect()

    # We can't really benchmark without frappe env setup.
    pass

if __name__ == "__main__":
    pass
