// OpenCode adapter - UNTESTED example, written from the OpenCode V2 plugin docs.
// Copy this folder to .opencode/plugins/phone-pill/ (or list it under "plugins" in opencode.json).
// Check the hook and event names against your OpenCode version: https://opencode.ai/docs/build/plugins/
import { execFile } from "node:child_process"

const phonectl = (...args: string[]) =>
  new Promise<void>((resolve) => execFile("phonectl", args, { timeout: 25000 }, () => resolve()))

export default {
  id: "phone-pill",
  async setup(ctx: any) {
    await ctx.tool.hook("execute.before", async (e: any) => {
      await phonectl("state", "working", String(e.input?.description ?? e.tool))
    })
    await ctx.tool.hook("execute.after", async () => {
      await phonectl("state", "working", "Thinking")
    })

    const controller = new AbortController()
    ;(async () => {
      for await (const ev of ctx.event.subscribe({ signal: controller.signal })) {
        if (ev.type === "session.execution.started") await phonectl("state", "working", "Thinking", "--new-task")
        else if (ev.type === "permission.asked") await phonectl("state", "waiting", "Permission needed")
        else if (ev.type === "form.created") await phonectl("state", "waiting", "Has a question for you")
        else if (/^session\.execution\.(succeeded|failed|interrupted)$/.test(ev.type)) await phonectl("state", "done")
      }
    })().catch(() => {})
    return () => controller.abort()
  },
}
