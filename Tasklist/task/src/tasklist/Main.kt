package tasklist

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.datetime.*
import java.io.File
import java.lang.RuntimeException

const val SEPARATOR = "+----+------------+-------+---+---+--------------------------------------------+"
const val HEADER    = "| N  |    Date    | Time  | P | D |                   Task                     |"
const val TASK_LIMIT = 44

enum class Colors(val color: String, val tag: String) {
    C("\u001B[101m \u001B[0m", "C"),
    H("\u001B[103m \u001B[0m", "H"),
    N("\u001B[102m \u001B[0m", "N"),
    L("\u001B[104m \u001B[0m", "L"),
    I("\u001B[102m \u001B[0m", "I"),
    T("\u001B[103m \u001B[0m", "T"),
    O("\u001B[101m \u001B[0m", "O");

    companion object {
        fun getPriority(code: String): String = when (code.lowercase()) {
            "c" -> C.color
            "h" -> H.color
            "n" -> N.color
            "l" -> L.color
            else -> ""
        }
    }
}

class Task(
    val description: String,
    val dueDate: String,
    val dueTime: String,
    val priority: String
) {
    fun inTime(): String {
        val dueDateAndTime = "${dueDate}T$dueTime:00Z".toInstant()
        val currentTime = Clock.System.now()
        val delta = dueDateAndTime.until(currentTime, DateTimeUnit.DAY, TimeZone.UTC)
        return when {
            delta < 0 -> Colors.I.color
            delta > 0 -> Colors.O.color
            else -> Colors.T.color
        }
    }
}

fun main() {
    val jsonFile = File("tasklist.json")
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    val taskListAdapter = moshi.adapter<List<Task?>>(
        Types.newParameterizedType(List::class.java, Task::class.java)
    )
    val tasks = mutableListOf<Task>()
    if (jsonFile.exists()) {
        val savedTasks = taskListAdapter.fromJson(jsonFile.readText()) ?: mutableListOf()
        for (t in savedTasks) if (t != null) tasks.add(t)
    }
    var userInput: String
    while (true) {
        println("Input an action (add, print, edit, delete, end):")
        userInput = readlnFiltered().lowercase()
        when (userInput) {
            "add" -> addTask(tasks)
            "print" -> showTasks(tasks)
            "edit" -> changeTasks(tasks) {
                val task2edit = tasks[it]
                tasks.removeAt(it)
                tasks.add(it, editTask(task2edit))
                println("The task is changed")
            }
            "delete" -> changeTasks(tasks) {
                tasks.removeAt(it)
                println("The task is deleted")
            }
            "end" -> {
                jsonFile.writeText(taskListAdapter.toJson(tasks))
                println("Tasklist exiting!")
                break
            }

            else -> println("The input action is invalid")
        }
    }
}

fun editTask(task: Task): Task {
    while (true) {
        println("Input a field to edit (priority, date, time, task):")
        when(readlnFiltered().lowercase()) {
            "priority" -> {
                return Task(
                    description = task.description,
                    dueDate = task.dueDate,
                    dueTime = task.dueTime,
                    priority = askPriority()
                )
            }
            "date" -> {
                return Task(
                    description = task.description,
                    dueDate = askDueDate(),
                    dueTime = task.dueTime,
                    priority = task.priority
                )
            }
            "time" -> {
                return Task(
                    description = task.description,
                    dueDate = task.dueDate,
                    dueTime = askDueTime(),
                    priority = task.priority
                )
            }
            "task" -> {
                return Task(
                    description = askDescription(),
                    dueDate = task.dueDate,
                    dueTime = task.dueTime,
                    priority = task.priority
                )
            }
            else -> println("Invalid field")
        }
    }
}

fun changeTasks(tasks: MutableList<Task>, changeTasks: (Int) -> Unit) {
    if (tasks.isEmpty()) println("No tasks have been input")
    else {
        showTasks(tasks)
        var taskNumber: Int
        while(true) {
            println("Input the task number (1-${tasks.size}):")
            val taskInput = readlnFiltered()
            if (!taskInput.isNumber() || taskInput.toInt() !in 1 .. tasks.size)
                println("Invalid task number")
            else {
                changeTasks(taskInput.toInt() - 1)
                return
            }
        }

    }
}

private fun String.isNumber(): Boolean = toIntOrNull() != null

private fun addTask(tasks: MutableList<Task>) {
    val priority = askPriority().uppercase()
    val dueDate = askDueDate()
    val dueTime = askDueTime()
    val task = askDescription()
    if (task.isEmpty()) println("The task is blank") else tasks.add(
        Task(
            description = task,
            dueDate = dueDate,
            dueTime = dueTime,
            priority = priority
        )
    )
}

private fun askDescription(): String {
    println("Input a new task (enter a blank line to end):")
    val task = StringBuilder()
    var taskInput = readlnFiltered()
    while (taskInput.isNotEmpty()) {
        task.append("$taskInput\n")
        taskInput = readlnFiltered()
    }
    return task.toString()
}

private fun askDueDate() = askForDueDateAndTime(
    "Input the date (yyyy-mm-dd):",
    """\d{4}-\d{1,2}-\d{1,2}""".toRegex(),
    "The input date is invalid"
) {
    var (year, month, day) = it.split("-")
    if (month.length == 1) month = "0$month"
    if (day.length == 1) day = "0$day"
    "$year-$month-${day}T00:00:00Z"
}.split("T").first()

private fun askDueTime() = askForDueDateAndTime(
    "Input the time (hh:mm):",
    """\d{1,2}:\d{1,2}""".toRegex(),
    "The input time is invalid"
) {
    var (hour, minute) = it.split(":")
    if (hour.length == 1) hour = "0$hour"
    if (minute.length == 1) minute = "0$minute"
    "2021-01-01T$hour:$minute:00Z"
}.split("T").last().removeSuffix(":00Z")

private fun askForDueDateAndTime(
    question: String,
    parser: Regex,
    warning: String,
    placeholder: (String) -> String
): String {
    var dueDateInput: String
    while (true) {
        println(question)
        dueDateInput = readlnFiltered()
        if (parser.matches(dueDateInput)) {
            try {
//                val (year,month,day) = dueDateInput.split("-")
//                return DateTimePeriod(years = year, months = month, days = day).toString()
                return placeholder(dueDateInput).toInstant().toString()
            } catch (e: RuntimeException) {
                println(warning)
            }
        }
        else println(warning)
    }
}

private fun askPriority(): String {
    println("Input the task priority (C, H, N, L):")
    var priority = readlnFiltered().lowercase()
    while (priority !in listOf("c", "h", "n", "l")) {
        println("Input the task priority (C, H, N, L):")
        priority = readlnFiltered().lowercase()
    }
    return Colors.getPriority(priority)
}

private fun readlnFiltered() = readln().removePrefix("> ").trim()

private fun showTasks(tasks: MutableList<Task>) {
    if (tasks.isEmpty()) println("No tasks have been input")
    else {
        println(SEPARATOR)
        println(HEADER)
        println(SEPARATOR)
        var idx = 1
        for (element in tasks) {
            val padding = if (idx > 9) " " else "  "
            val taskLine: StringBuilder = StringBuilder("| $idx$padding| ${element.dueDate} | ${element.dueTime} | ${element.priority} | ${element.inTime()} |")

            val taskDescriptionList = element.description.trim().split("\n")
            for (description in taskDescriptionList) {
                val descriptionSliced = description.chunked(TASK_LIMIT)
                for (ds in descriptionSliced) {
                    val space = " "
                    if (taskLine.endsWith(" |"))
                        taskLine.append("$ds${space.repeat(TASK_LIMIT - ds.length)}|\n")
                    else {
                        taskLine.append("|${space.repeat(4)}|${space.repeat(12)}|${space.repeat(7)}|${space.repeat(3)}|${space.repeat(3)}|$ds${
                            space.repeat(
                                TASK_LIMIT - ds.length
                            )
                        }|\n")
                    }
                }
            }
            println(taskLine.toString().trim())
            println(SEPARATOR)
            idx++
        }
    }
}


