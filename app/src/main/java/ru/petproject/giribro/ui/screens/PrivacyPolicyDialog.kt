package ru.petproject.giribro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.petproject.giribro.R

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val developer = stringResource(R.string.legal_developer)
    val email = stringResource(R.string.legal_contact_email)
    val url = stringResource(R.string.legal_privacy_url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Политика конфиденциальности") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 460.dp)
            ) {
                Text(
                    "Дата последнего обновления: 12 ноября 2026 г.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PolicySection(
                    title = "1. Общие сведения",
                    text = "Приложение «ГириБро» предназначено для ведения дневника " +
                        "гиревых тренировок, учёта веса и замеров тела, просмотра " +
                        "нормативов, календаря соревнований и создания фото-карточек. " +
                        "Разработчик: $developer."
                )
                PolicySection(
                    title = "2. Какие данные используются",
                    text = "Приложение не собирает, не передаёт и не продаёт персональные " +
                        "данные. У него нет доступа к интернету, аккаунтов, облачной " +
                        "синхронизации, рекламы, аналитики и трекеров.\n\n" +
                        "Тренировки, вес, замеры, заметки и настройки, которые вводит " +
                        "пользователь, хранятся только в локальной базе на устройстве."
                )
                PolicySection(
                    title = "3. Фото-карточки и резервные копии",
                    text = "Фото-карточки формируются локально. При нажатии «Поделиться» " +
                        "файл передаётся в выбранное пользователем приложение через " +
                        "системное меню Android. Резервная копия создаётся только по " +
                        "действию пользователя и сохраняется в выбранное им место. " +
                        "Разработчик не получает копию этих файлов."
                )
                PolicySection(
                    title = "4. Разрешения",
                    text = "Для таймера используются уведомления, foreground-сервис и " +
                        "вибрация. Приложение не запрашивает доступ к контактам, " +
                        "геолокации, камере, микрофону и внешнему хранилищу."
                )
                PolicySection(
                    title = "5. Хранение и удаление",
                    text = "Данные хранятся на устройстве до тех пор, пока пользователь " +
                        "их не удалит. Удалить тренировки и замеры можно в настройках " +
                        "приложения. Полностью удалить данные можно через настройки " +
                        "Android или удалив приложение."
                )
                PolicySection(
                    title = "6. Передача третьим лицам",
                    text = "Разработчик не передаёт данные третьим лицам. Передача " +
                        "возможна только по явному действию пользователя через системное " +
                        "меню «Поделиться» или при сохранении резервной копии."
                )
                PolicySection(
                    title = "7. Контакты",
                    text = "Разработчик: $developer\nE-mail: $email\n" +
                        "Публичная версия: $url"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun PolicySection(title: String, text: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
}
