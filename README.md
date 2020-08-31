# What
If you're trying to access the NWWS-OI feed but you don't want to buy a satelite dish, scrape info from weather.gov, or figure out how XMPP works then you've found the right place.  This is the source for a server that provides the same information (eg WMO products) in JSON format.

## How
This server is hosted at [http://nwws.beechens.com](http://nwws.beechens.com), which you ar welcome to use.  The server's homepage has detailed instructions for how to consume the information it provides.

Note: I can no longer afford to provision the server hsoting this information.  The address above no longer works.

## Reminder to myself
Redeploy the server by running
```bash
 mvn clean package appengine:deploy
```
in the project's root directory.

