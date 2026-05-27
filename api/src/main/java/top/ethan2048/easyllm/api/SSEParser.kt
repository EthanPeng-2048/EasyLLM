package top.ethan2048.easyllm.api

/**
 * SSE (Server-Sent Events) 解析器
 * 解析 OpenAI 流式响应格式: data: {json}\n\n
 */
class SSEParser {

    private var currentData = StringBuilder()

    /**
     * 逐行解析 SSE 数据
     * @param line 一行 SSE 文本
     * @return 解析出的 data 内容，如果行不包含完整数据则返回 null
     */
    fun parseLine(line: String): String? {
        // 空行表示事件结束
        if (line.isEmpty()) {
            val data = currentData.toString().trim()
            currentData = StringBuilder()
            return if (data.isNotEmpty()) data else null
        }

        // 注释行
        if (line.startsWith(":")) {
            return null
        }

        // data 行
        if (line.startsWith("data:")) {
            val dataContent = line.removePrefix("data:").trim()
            if (dataContent == "[DONE]") {
                currentData = StringBuilder()
                return "[DONE]"
            }
            currentData.append(dataContent)
            return null
        }

        // event:, id:, retry: 等其他 SSE 字段暂不处理
        return null
    }

    /**
     * 重置解析器状态
     */
    fun reset() {
        currentData = StringBuilder()
    }
}
