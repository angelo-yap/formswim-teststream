## **Form Swim \- Project Proposal**

TestStream (TCMS) \- Group 19

## **The Problem**

As FormSwim develops and maintains a hardware device, they have created and used many test cases. These are managed in the testing software QMetry, and are often exported to Excel for large edits or restructuring. This workflow has created several challenges for the QA engineers at FormSwim we discuss below.

The first issue is related to searching through large folders. This is challenging in QMetry as the native search functionality is limited to specific fields and does not support flexible or comprehensive queries. Another issue pertains to bulk editing and deletion of test cases. These features are also restricted, with deletions capped at 100 test cases (items) per operation despite test case repositories containing thousands of cases. Splitting large test suites into smaller, more manageable groups requires extensive manual effort, and there is no clear built-in method for categorizing or organizing test cases. Additionally, reviewing test results often involves analyzing raw spreadsheet data rather than intuitive visual summaries, making interpretation more time-consuming.

As test suites grow larger, these issues reduce productivity and increase the risk of errors.

## **Currently**

Currently, test cases are primarily managed using QMetry’s built-in interface. When large-scale edits, restructuring, or reviews are required, teams commonly have to manually perform them.

Basic organization is handled within QMetry’s folder system. However, advanced operations such as bulk editing, batch deletion, and test suite restructuring are limited, with no efficient built-in mechanisms for grouping or reorganizing larger numbers of test cases. As a result, users often rely on spreadsheets to perform these operations or just manually selecting items and grouping them.

Searching is currently handled within QMetry using a strict, field-limited approach that primarily matches only test case titles. However, test cases contain multiple important fields, such as step descriptions and summaries that users may want to search across. As a result, locating test cases based on these fields requires manual review, making the process inefficient and time-consuming. 

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
**Actors**:

- QA Engineer

**Iteration**: 

- Iteration 1

**Triggers:** 

- User submits registration form.  
- User submits login credentials.  
- User attempts to access a protected route.

**Post Conditions:** 

- A new user account is successfully created and stored securely.  
- A secure session is established to authenticated user.  
- Access to workspace features is granted to authenticated user.  
- Unauthorized users are redirected to login page.

**Error Conditions:** 

- Invalid or incorrect login credentials are submitted.  
- Login session expires due to inactivity.  
- Duplicate account registration using an existing email or username.

**Acceptance tests:** 

- User can successfully register with valid credentials  
- User can log in with correct credentials and access the workspace.  
- User is redirected to login page when attempting to access without authentication.  
- Incorrect login attempts display appropriate error messages.

**Epic 2: Excel Upload, Download, Storage**  
**Actors**

- QA Engineer (Primary)  
- Supervisors 

**Iteration**

- Iteration 1

**Triggers**

- User uploads test cases in excel form  
  - Uploads are stored in database  
- User requests test cases downloaded

**Post Conditions**

- Successful upload of test cases to database  
- Storage of uploaded test cases  
- Successful download from database to user of requested test cases

**Error Conditions** 

- Format is invalid for test case examples or missing  
- Missing fields or steps  
- Duplicate uploads (rejected automatically)

**Acceptance tests**

- User can upload a test case, view it on another device(primative UI), and download it  
- Duplicate test cases are blocked or rejected  
- Missing fields are rejected or prompted to be fixed before uploading

**Epic 3: Search & Bulk Edit**   
**Actors**

- QA Engineer (Primary)

**Iteration**

- Iteration 2

**Triggers**

- User searches test cases using keywords  
- User searches test cases using custom tags  
- User edits large quantities of test cases  
- Edit fields *step summary, test, data,* and *title*

**Post Conditions**

- All selected test cases are available to view and highlighted  
- All selected test cases are systematically updated with new values after editing  
- Indicator of how many occurrences found  
- Indicator of how many edits made

**Error Conditions** 

- Network error during mass edits  
- Editing read-only data

**Acceptance tests**

- Bulk edit replaces data across 1000+ test cases  
- Error message trying to edit protected data  
- Validate that searching identifies ALL occurrences of desired keyword  
- Validate that searching identifies ALL test cases with custom tag

**Epic 4: Split & Organize**   
**Actors**

- QA Engineer (Primary)  
- Test Lead / Manager (Secondary)

**Iteration**

- Iteration 2 (Features)

**Triggers**

- User selects a test suite or folder to organize  
- User selects multiple test cases and chooses to Split or Organize  
- User applies grouping by component, tag, status or creates a manual group  
- User uses grouping or select and chooses to export by those rules  
- User clicks save/apply

**Post Conditions**

- Test cases are organized into the user-defined groupings  
- Any selected split rule is applied correctly  
- Specified group names and structures are saved properly and visible in the UI  
- Test case tag reflects user-defined choice

**Error Conditions** 

- Test suite/case not selected while grouping  
- Invalid or misinputted grouping rules  
- Missing required fields in test cases  
- Network/server error while saving

**Acceptance tests**

- Manual Selection creates a new group  
  - Given a test suite is active and x test cases are selected  
  - When the user creates a group title “demo” and clicks apply  
  - Then the grouping “demo” appears in the test suite and contains the x test cases  
- Tag assigns new category for test case  
  - Given a test suite is active and user has created “test” tag  
  - When the user clicks on test case and selects “test” tag and clicks save  
  - Then the test case now has membership to the “test” category  
- Split by field  
  - Given a test suite containing test cases with different components and tags  
  - When the user selects split by component or tag  
  - Then the system creates groups based on the specified rule  
- Export by group  
  - Given a test suite with multiple groups  
  - When the user selects the group and clicks export by group  
  - Then the system will export the selected group only in an xls file  
- No selection error  
  - Given no test cases are selected  
  - When the user clicks create group  
  - Then the system shows an error message to the user and does not create group

**Epic 5: Undo & Redo Changes**  
**Actors**  
QA Engineer (Primary)  
**Iteration**

- Iteration 2 (Features)

**Triggers**

- User edits a test case or test suite in the workspace  
- User clicks “Undo” or uses Ctrl/Cmd \+ Z  
- User clicks “Redo” or uses Ctrl/Cmd \+ Y or Ctrl/Cmd \+ Shift \+ Z  
- User opens “History” and selects earlier saved version (Like in Google Docs)

**Post Conditions**

- Most recent edit is reverted when undo is triggered  
- Redo re-applies most recently undone change  
- When restoring from “History”, test suite is reverted to the selected version  
- The editor and any visualization is updated to reflect the reverted changes/restored state  
- History entry is recorded alongside timestamp and user

**Error Conditions** 

- Undo is triggered when there is no changes to undo  
- Redo is triggered when there are no changes to redo  
- History cannot be loaded due to lack of history/network/server error  
- Selected history version does not exist/cannot be restored  
- If permissions are added, user attempts rollback without permission  
- Restore fails validation due to missing fields

**Acceptance tests**

- Basic undo  
  - User can make an edit and successfully undo it  
  - Undo does not affect unrelated test cases  
- Basic redo  
  - User can undo a change and redo it successfully  
- No undo available  
  - If no changes exist, Undo is disabled or an error message is shown   
- History restore  
  - Given a test suite has multiple saved versions, when the user selects an older version and clicks “Restore”, the test suite content matches the selected version and is shown in the UI  
- Restore Confirmation  
  - When a user clicks Restore, the app prompts the user for confirmation. “Cancel” makes no change; “Confirm” restores the changes.  
- Server error handling   
  - If restore (undo, redo, restore from history) fails due to server/network error, systems shows the appropriate error message and keeps the current version unchanged.

**Epic 6: Visualization Results**  
**Actors**

- Qa Engineer (Primary)  
- Test Lead / Manager (Secondary)

**Iteration**

- Iteration 3

**Triggers**

- User uploads a “Test Result” spreadsheet  
- User manually selects the test result for a test case  
- User opens the results dashboard for a Test Cycle  
- User clicks a chart segment / metric   
- Users exports a summary report or visualization

**Post Conditions**

- Dashboard generated showing key metrics (Pass/Fail, not executed, pass rate)  
- Chart reflect the currently selected filters and grouping rules  
- System stores visualization state for that run (saved filters)

**Error Conditions**

- Invalid result spreadsheet format/headers  
- Missing required columns (e.g., test case id/name, status, execution timestamp)  
- Results data contains unmapped values

**Acceptance tests**

- Given a result file with known counts of Pass/Fail/Blocked/Not Run, when the dashboard loads, then each metric matches the file  
- Given test cases that were previously split into groups, when the user selects group “demo”, then the dashboard shows only that group’s results  
- Export works  
- Given the results file contains an unknown value, system shows a clear warning and continues without corrupting metrics

# **Team Work Distribution and Team Roles** 

For the first iteration, we have decided to focus on our authentication system, simple UI flow (wireframes) and excel uploading and storage. We have decided to assign 3 members to work on the backend, implementing and testing the iteration features, while 2 members are focusing on prototyping the UI for coming features and UX. This project has a sufficient amount of work for a team of 5\. Right now we have 6 main features that we are aiming to implement, each feature has its intricacies and will be a solid workload for the five of us.  
**Flexible Distributions**  
Our Backend team will be

- Michael Bazett  
- Angelo  
- Andy Ngo

Our Frontend team will be:

- Jayden Truong  
- Armin Mehrabian




