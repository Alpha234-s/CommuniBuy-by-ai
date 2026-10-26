import { ElMessageBox } from 'element-plus'
export async function confirmAction(message: string, title = '请确认操作'): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: '确认',
      cancelButtonText: '返回',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
}
