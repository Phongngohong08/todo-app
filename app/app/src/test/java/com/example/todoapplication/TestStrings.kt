package com.example.todoapplication

import com.example.todoapplication.i18n.AppStrings
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit test chạy trên JVM không có Android Resources → nạp thẳng file strings.xml của module
 * và ánh xạ id trong R sang tên khóa. Mặc định tiếng Việt (values-vi) để test kiểm tra đúng chữ người dùng thấy.
 *
 * Dùng: `init { TestStrings.install() }` (hoặc trong @Before) ở test gọi code có str(...) / plural(...).
 */
object TestStrings {
    private val cache = mutableMapOf<String, AppStrings.Source>()

    fun install(lang: String = "vi") {
        AppStrings.source = cache.getOrPut(lang) { load(lang) }
    }

    private fun load(lang: String): AppStrings.Source {
        val dir = if (lang == "en") "values" else "values-$lang"
        val file = listOf("src/main/res/$dir/strings.xml", "app/src/main/res/$dir/strings.xml").map(::File).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableMapOf<String, Pair<String, Boolean>>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val arrays = mutableMapOf<String, List<String>>()
        val root = doc.documentElement
        root.getElementsByTagName("string").forEachElement {
            strings[it.getAttribute("name")] = unescape(it.textContent) to (it.getAttribute("formatted") != "false")
        }
        root.getElementsByTagName("plurals").forEachElement { p ->
            val items = mutableMapOf<String, String>()
            p.getElementsByTagName("item").forEachElement { items[it.getAttribute("quantity")] = unescape(it.textContent) }
            plurals[p.getAttribute("name")] = items
        }
        root.getElementsByTagName("string-array").forEachElement { a ->
            val items = mutableListOf<String>()
            a.getElementsByTagName("item").forEachElement { items += unescape(it.textContent) }
            arrays[a.getAttribute("name")] = items
        }
        val names = idNames()
        return object : AppStrings.Source {
            override fun string(id: Int, args: Array<out Any?>): String {
                val (value, formatted) = strings.getValue(names.getValue(id))
                return if (formatted && args.isNotEmpty()) String.format(value, *args) else value
            }

            override fun plural(id: Int, count: Int, args: Array<out Any?>): String {
                val items = plurals.getValue(names.getValue(id))
                val value = (if (count == 1) items["one"] else null) ?: items.getValue("other")
                return String.format(value, *(if (args.isEmpty()) arrayOf(count) else args))
            }

            override fun array(id: Int): List<String> = arrays.getValue(names.getValue(id))
        }
    }

    private fun idNames(): Map<Int, String> =
        listOf(R.string::class.java, R.plurals::class.java, R.array::class.java)
            .flatMap { cls -> cls.fields.map { it.getInt(null) to it.name } }
            .toMap()

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                out.append(if (n == 'n') '\n' else if (n == 't') '\t' else n)
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private inline fun org.w3c.dom.NodeList.forEachElement(block: (Element) -> Unit) {
        for (i in 0 until length) block(item(i) as Element)
    }
}
