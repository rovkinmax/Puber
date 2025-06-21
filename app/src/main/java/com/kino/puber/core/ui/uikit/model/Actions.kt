package com.kino.puber.core.ui.uikit.model

import kotlinx.datetime.LocalDate

interface UIAction

sealed class CommonAction : UIAction {
    data object ContinueClicked : CommonAction()
    data object RetryClicked : CommonAction()
    data object Refresh : CommonAction()
    data object SnackBarDismissed : CommonAction()
    data object SnackBarActionPerformed : CommonAction()
    class ItemSelected<T>(val item: T) : CommonAction()
    class ItemFocused<T>(val item: T) : CommonAction()
    class ItemRemoved<T>(val item: T) : CommonAction()
    class TextChanged(val text: String, val tag: Any) : CommonAction()
    class FieldFocusChanged(val tag: Any, val focused: Boolean) : CommonAction()
    class DateRangePickedAction(val dateFrom: LocalDate?, val dateTo: LocalDate?) : CommonAction()
    class DatePickedAction(val date: LocalDate?, val tag: Any? = null) : CommonAction()
    class DropDownMenuOpen(val isOpen: Boolean) : CommonAction()
    class RadioButtonChecked(val isChecked: Boolean, val tag: Any) : CommonAction()
    data object LoadMore : CommonAction()
    data object ReloadNextPage : CommonAction()
    data object OpenPermissionSettings : CommonAction()
    data object Share : CommonAction()
    data object OnResume : CommonAction()
    data object ChooseDate : CommonAction()
}