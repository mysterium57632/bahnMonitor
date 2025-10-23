package de.paull

import org.w3c.dom.*

class ChildIterator(private val list: NodeList) : Iterator<Node> {

    private var current = list.item(0)

    override fun next(): Node {
        val ret = current
        current = current.nextSibling
        return ret
    }

    override fun hasNext(): Boolean {
        return current != null
    }

}