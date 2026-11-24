# Android MVVM Architecture Sample

Selected code samples extracted from a production Android application to demonstrate clean code practices, architecture, and modern Android development patterns.

> **Note:** Due to a Non-Disclosure Agreement (NDA), the full source code and backend endpoints cannot be published publicly. The files in this repository represent isolated, anonymized layers of the application.

## Tech Stack & Architecture
* **Language:** Kotlin
* **Architecture:** MVVM (Model-View-ViewModel) + Repository Pattern
* **UI:** XML Views, ViewBinding, RecyclerView (`ListAdapter` & `DiffUtil`)
* **Asynchronous Programming:** Kotlin Coroutines & Flow / LiveData
* **Networking:** Retrofit & OkHttp
* **Dependency Injection:** Hilt / Koin 

## What's Included
* **UI Layer (`Fragment` & `XML`):** Lifecycle-aware view binding and reactive UI updates.
* **Adapter (`RecyclerView`):** Efficient list rendering using `DiffUtil`.
* **Presentation Layer (`ViewModel`):** State management and coroutine-based data fetching.
* **Data Layer (`Repository` & `ApiService`):** Clean separation of network calls and error handling.
