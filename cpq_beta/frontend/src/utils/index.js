import TaskStatusIcon from '@/components/Icons/TaskStatusIcon.vue'
import TaskPriorityIcon from '@/components/Icons/TaskPriorityIcon.vue'
import { useDateFormat, useTimeAgo } from '@vueuse/core'
import { usersStore } from '@/stores/users'
import { gemoji } from 'gemoji'
import { toast } from 'frappe-ui'
import { h } from 'vue'
import Sval from 'sval'

export function createToast(options) {
  toast({
    position: 'bottom-right',
    ...options,
  })
}

export function formatTime(seconds) {
  const days = Math.floor(seconds / (3600 * 24))
  const hours = Math.floor((seconds % (3600 * 24)) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainingSeconds = Math.floor(seconds % 60)

  let formattedTime = ''

  if (days > 0) {
    formattedTime += `${days}d `
  }

  if (hours > 0 || days > 0) {
    formattedTime += `${hours}h `
  }

  if (minutes > 0 || hours > 0 || days > 0) {
    formattedTime += `${minutes}m `
  }

  formattedTime += `${remainingSeconds}s`

  return formattedTime.trim()
}

export function dateFormat(date, format) {
  const _format = format || 'DD-MM-YYYY HH:mm:ss'
  return useDateFormat(date, _format).value
}

export function timeAgo(date) {
  return useTimeAgo(date).value
}

export const dateTooltipFormat = 'ddd, MMM D, YYYY h:mm A'

export function taskStatusOptions(action, data) {
  return ['Backlog', 'Todo', 'In Progress', 'Done', 'Canceled'].map(
    (status) => {
      return {
        icon: () => h(TaskStatusIcon, { status }),
        label: status,
        onClick: () => action && action(status, data),
      }
    }
  )
}

export function taskPriorityOptions(action, data) {
  return ['Low', 'Medium', 'High'].map((priority) => {
    return {
      label: priority,
      icon: () => h(TaskPriorityIcon, { priority }),
      onClick: () => action && action(priority, data),
    }
  })
}

export function openWebsite(url) {
  const newWindow = window.open(url, '_blank', 'noopener,noreferrer')
  if (newWindow) {
    newWindow.opener = null
  }
}

export function htmlToText(html) {
  if (typeof DOMParser === 'undefined') {
    // Fallback if DOMParser is not available (e.g. Node.js environment)
    const div = document.createElement('div')
    div.innerHTML = html
    return div.textContent || div.innerText || ''
  }
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  return doc.body.textContent || doc.body.innerText || ''
}

export function secondsToDuration(seconds) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const _seconds = Math.floor((seconds % 3600) % 60)

  if (hours == 0 && minutes == 0) {
    return `${_seconds}s`
  } else if (hours == 0) {
    return `${minutes}m ${_seconds}s`
  }
  return `${hours}h ${minutes}m ${_seconds}s`
}

export function formatNumberIntoCurrency(value, currency = 'INR') {
  if (value) {
    return value.toLocaleString('en-IN', {
      maximumFractionDigits: 0,
      style: 'currency',
      currency: currency ? currency : 'INR',
    })
  }
  return ''
}

export function startCase(str) {
  return str.charAt(0).toUpperCase() + str.slice(1)
}

export function validateEmail(email) {
  let regExp =
    /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/
  return regExp.test(email)
}

export function setupAssignees(data) {
  let { getUser } = usersStore()
  let assignees = data._assign || []
  data._assignedTo = assignees.map((user) => ({
    name: user,
    image: getUser(user).user_image,
    label: getUser(user).full_name,
  }))
}

function getActionsFromScript(script, obj) {
  try {
    const interpreter = new Sval()
    // By default, sval runs in sandBox mode and does not have access to global objects.
    // However, some custom scripts might expect standard globals.
    // To prevent total breakage while still preventing RCE/XSS (as the sandbox is maintained),
    // we explicitly whitelist some safe globals.
    interpreter.import({
      console: console,
      Math: Math,
      JSON: JSON,
      Date: Date,
      String: String,
      Number: Number,
      Array: Array,
      Object: Object,
      Boolean: Boolean,
    })
    interpreter.run(script)
    interpreter.run('if (typeof setupForm !== "undefined") { exports.setupForm = setupForm; }')
    if (typeof interpreter.exports.setupForm === 'function') {
      let formScript = interpreter.exports.setupForm(obj)
      return formScript?.actions || []
    }
  } catch (e) {
    console.error('Error executing custom script', e)
  }
  return []
}

export function setupCustomActions(data, obj) {
  if (!data._form_script) return []

  let actions = []
  if (Array.isArray(data._form_script)) {
    data._form_script.forEach((script) => {
      actions = actions.concat(getActionsFromScript(script, obj))
    })
  } else {
    actions = getActionsFromScript(data._form_script, obj)
  }

  data._customActions = actions
}

function getActionsFromListScript(script, obj) {
  try {
    const interpreter = new Sval()
    interpreter.import({
      console: console,
      Math: Math,
      JSON: JSON,
      Date: Date,
      String: String,
      Number: Number,
      Array: Array,
      Object: Object,
      Boolean: Boolean,
    })
    interpreter.run(script)
    interpreter.run('if (typeof setupList !== "undefined") { exports.setupList = setupList; }')
    if (typeof interpreter.exports.setupList === 'function') {
      let listScript = interpreter.exports.setupList(obj)
      return {
        actions: listScript?.actions || [],
        bulk_actions: listScript?.bulk_actions || [],
      }
    }
  } catch (e) {
    console.error('Error executing list script', e)
  }
  return { actions: [], bulk_actions: [] }
}

export function setupListActions(data, obj = {}) {
  if (!data.list_script) return []

  let actions = []
  let bulkActions = []

  if (Array.isArray(data.list_script)) {
    data.list_script.forEach((script) => {
      let _actions = getActionsFromListScript(script, obj)
      actions = actions.concat(_actions.actions)
      bulkActions = bulkActions.concat(_actions.bulk_actions)
    })
  } else {
    let _actions = getActionsFromListScript(data.list_script, obj)
    actions = _actions.actions
    bulkActions = _actions.bulk_actions
  }

  data.listActions = actions
  data.bulkActions = bulkActions
}

export function errorMessage(title, message) {
  createToast({
    title: title || 'Error',
    text: message,
    icon: 'x',
    iconClasses: 'text-red-600',
  })
}

export function copyToClipboard(text) {
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text)
      .then(show_success_alert)
      .catch((err) => {
        errorMessage('Failed to copy', err?.message || 'Clipboard write failed')
      })
  } else {
    errorMessage('Failed to copy', 'Clipboard API is not supported or not in a secure context')
  }
  function show_success_alert() {
    createToast({
      title: 'Copied to clipboard',
      text: text,
      icon: 'check',
      iconClasses: 'text-green-600',
    })
  }
}

export function isEmoji(str) {
  const emojiList = gemoji.map((emoji) => emoji.emoji)
  return emojiList.includes(str)
}

export function isTouchScreenDevice() {
	return "ontouchstart" in document.documentElement;
}
