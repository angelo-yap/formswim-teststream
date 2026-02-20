## **Form Swim \- Project Proposal**

TestStream (TCMS) \- Group 19

## **The Problem**

As FormSwim develops and maintains a hardware device, they have created and used many test cases. These are managed in the testing software QMetry, and are often exported to Excel for large edits or restructuring. This workflow has created several challenges for the QA engineers at FormSwim we discuss below.

The first issue is related to searching through large folders. This is challenging in QMetry as the native search functionality is limited to specific fields and does not support flexible or comprehensive queries. Another issue pertains to bulk editing and deletion of test cases. These features are also restricted, with deletions capped at 100 test cases (items) per operation despite test case repositories containing thousands of cases. Splitting large test suites into smaller, more manageable groups requires extensive manual effort, and there is no clear built-in method for categorizing or organizing test cases. Additionally, reviewing test results often involves analyzing raw spreadsheet data rather than intuitive visual summaries, making interpretation more time-consuming.

As test suites grow larger, these issues reduce productivity and increase the risk of errors.

## **The Current Solution**

Currently, test cases are primarily managed using QMetry’s built-in interface. When large-scale edits, restructuring, or reviews are required, teams commonly have to manually perform them.

Basic organization is handled within QMetry’s folder system. However, advanced operations such as bulk editing, batch deletion, and test suite restructuring are limited, with no efficient built-in mechanisms for grouping or reorganizing larger numbers of test cases. As a result, users often rely on spreadsheets to perform these operations or just manually selecting items and grouping them.

Searching is currently handled within QMetry using a strict, field-limited approach that primarily matches only test case titles. However, test cases contain multiple important fields, such as step descriptions and summaries that users may want to search across. As a result, locating test cases based on these fields requires manual review, making the process inefficient and time-consuming. 

**Competitive Analysis**

While QMetry serves as the industry standard for hardware-integrated test execution, its primary strength lies in formal tracking rather than the agile maintenance required for large-scale test suites. At the enterprise scale of Form Swim, the platform's 100 time operation caps and rigid title only search parameters create significant bottlenecks. To bypass this, teams frequently resort to Microsoft Excel for its superior bulk-editing and filtering capabilities. However, Excel introduces a disconnect from the database, lacking essential security protocols essential for professional Quality Assurance. TestStream is designed to bridge this gap by combining the powerful, open-ended flexibility of a spreadsheet with the structural integrity of a dedicated management platform.

## **Proposed System** 

TestStream will be developed as a web-based platform that serves as a workspace for editing, organizing, and reviewing test cases before submission into QMetry. It will provide advanced search across fields, bulk editing, mass deletion, reorganization tools, and visual summaries of test results. The system will replace spreadsheet based and tedious workflows with an intentional editor app designed for scalability and usability.

## **Impact**

This tool aims to help QA engineers at Form Swim manage large volumes of test 	suites. Instead of relying on manual spreadsheet edits and slow navigation through large folders, users will be able to use advanced queries to search through test cases, apply bulk edits or deletions, split large suits into smaller files, and undo mistakes. The goal is to reduce time spent on repetitive work and lower the risk of errors when handling hundreds or thousands of test cases.

## **Targeted Audience** \- Form Swim QA Team and QMetry users

## **Detailed Epics / User Capabilities**

Our five Epic’s are detailed below. Here is an overview list:

* Login system  
* Excel Upload, Download, Storage  
* Search and Bulk Edit  
* Split and Organize  
* Undo Changes (Rollback)  
* Visualization

**Epic 1: Login System** 

Scheduled for Iteration 1, the Login System epic enables the primary actor, the QA Engineer, to securely access the TestStream workspace . This functionality is triggered when a user submits a registration form, enters login credentials, or attempts to access a protected route within the application . Upon successful interaction, a new user account is created and stored securely, a secure session is established for the authenticated user, and access to workspace features is granted . Conversely, the system ensures that unauthorized users are redirected to the login page to maintain security.

The system is designed to handle several error conditions, including the submission of invalid credentials, session expiration due to inactivity, or duplicate account registration attempts using an existing email or username . Success is verified through acceptance tests ensuring users can successfully register and log in with valid credentials to access the workspace . Furthermore, the system must demonstrate that attempts to access protected areas without authentication result in a redirection and that incorrect credentials prompt the display of appropriate error messages.

**Epic 2: Excel Upload, Download & Storage**

This feature enables QA Engineers and Supervisors to upload test cases via Excel files, which are then stored in a database and made available for download on demand. It represents the core data pipeline for getting test cases into and out of the system.

When a user uploads an Excel file containing test cases, the system validates the file and stores the contents in the database. Users can later retrieve their test cases by downloading them directly from the database. To ensure data integrity, the system automatically rejects duplicate uploads, flags invalid or improperly formatted files, and prompts users to correct any missing fields or steps before a submission is accepted.

The feature is considered complete when a user can successfully upload a test case, view it on a separate device through a basic UI, and download it back. Guardrails should be in place to block duplicates and enforce required fields throughout the process.

**Epic 3: Search & Bulk Edit** 

Scheduled for Iteration 2, the Search and Bulk Edit epic allows the primary actor, the QA Engineer, to manage vast quantities of test data with efficiency . The functionality is triggered when a user searches for test cases using specific keywords or custom tags, targeting fields such as the step summary, test, data, and title . Users can then select large quantities of test cases to perform mass edits. Upon completion, all selected test cases are highlighted and systematically updated with new values, while the interface provides indicators for the number of occurrences found and edits made.

The system is designed to manage error conditions such as network failures during mass edits or attempts to modify read-only data . Acceptance tests will validate that searching accurately identifies all occurrences of keywords and custom tags. Furthermore, the system must successfully process bulk edits across 1000 or more test cases and display a clear error message if a user attempts to edit protected data.

**Epic 4: Split & Organize** 

The Split and Organize feature, planned for Iteration 2, allows primary actors like QA Engineers and secondary actors such as Test Leads or Managers to manage large-scale test suites with precision . The process is initiated when a user selects a specific test suite or folder and chooses to either split or organize the contents . Users can apply organizational logic by grouping cases according to component, status, or custom tags, or they may manually define a new group. Upon clicking save, the system ensures that all test cases are correctly categorized within the UI and that the specified split rules are applied.

To maintain system integrity, TestStream is built to handle error conditions such as missing required fields, invalid grouping rules, or network failures during the saving process . Furthermore, the system will prevent operations if no test cases are selected, displaying a clear error message to the user . The success of this epic is verified through rigorous acceptance testing. For instance, a user should be able to select multiple cases and create a group titled "demo," which should then be immediately visible and contain the correct items . The system must also support tagging test cases for category membership and automatically splitting suites based on field values . Finally, users must be able to export these specific groups into XLS files, ensuring that only the selected group data is included in the download.

**Epic 5: Undo & Redo Changes**

Scheduled for Iteration 2, the Undo and Redo epic provides the primary actor, the QA Engineer, with a reliable mechanism to manage version control within the workspace. This functionality is triggered when a user performs edits, utilizes keyboard shortcuts like Ctrl/Cmd \+ Z or Ctrl/Cmd \+ Y, or accesses the "History" menu to select a previously saved version . Upon activation, the most recent edit is reverted or re-applied, or the entire test suite is restored to a selected historical state . The editor and all associated visualizations are then updated to reflect the change, while a history entry is recorded with a specific timestamp and user ID.

To ensure robust performance, the system manages several error conditions, including instances where undo or redo is triggered without available changes, or when network and server errors prevent the history from loading . The application also accounts for cases where a selected history version cannot be restored or fails validation due to missing fields . Acceptance tests verify that basic undo and redo operations function successfully without affecting unrelated test cases . If no changes are available, the undo feature will be disabled or show an error message. For larger restorations, the UI must match the selected historical version after the user confirms the action through a prompt, while any server failures will keep the current version unchanged and notify the user .

**Epic 6: Visualization Results**

This feature gives QA Engineers and Test Leads a dashboard for understanding the outcomes of a Test Cycle. It brings visual interpretation to the test cases through charts and metrics. This is a feature planned for a later cycle of this project, and is to push our team’s technical and design knowledge and skills.

Results can enter the system in one of two ways, either through a bulk upload of a results spreadsheet or by a user manually selecting the outcome for an individual test case. Once results are loaded, the dashboard automatically generates a summary of key metrics including pass/fail counts, not-executed cases, and an overall pass rate. Users can filter and group the data to focus on specific subsets, such as a particular demo group, and the system remembers those filter selections for that run so context isn't lost between sessions. To provide deeper analytical value, the system will interface with the OpenAI REST API, gathering natural language insights and trend summaries based on the uploaded execution data. 

This feature must validate any uploaded results file before processing it, rejecting files with invalid formats or missing required columns like test case ID, status, or execution timestamp. If an unknown result value is encountered, the system fails loudly and allows the mistake or error to be fixed without corrupting any data.

# **Team Work Distribution and Team Roles** 

For the first iteration, we have decided to focus on our authentication system, a simple UI flow and our first Epic of excel uploading and storage. We have decided to assign 3 members to work on the backend, implementing and testing the iteration features, while 2 members are focusing on prototyping the UI for coming features and UX. This project has a sufficient amount of work for a team of 5\. Right now we have 6 main features that we are aiming to implement, each feature has its intricacies and will be a solid workload for the five of us. Our backend team will be Michael Bazett, Angelo Yap*,* Andy Ngo. Our frontend team will currently be Jayden Troung and Armin Mehrabian. These are subject to change as we iterate through the project.

