Overview
------
This module adds feature that helps to sync issues, projects, time entries, versions, trackers from the Redmine's REST API.
All the issues from the Redmine Projects are synced to the ABS with just one button click in batches.

  
Dependencies
------

* axelor-business-support
* redmine-java-api

Configuration
------
* **Authentication** 

>Most of the time, the API requires authentication. To enable the API-style authentication, you have to check Authentication required and Enable REST API in Administration -> Settings -> Authentication & API.
		
* **Requirements**

>1. Title (URI) (i.e. https://www.hostedredmine.com)
>2. API access Key (i.e. alpha-numeric key)
>    - After enabling REST API, you can find your API Access key on your account page ( My Account ) when logged in, on the right-hand pane of the default layout.